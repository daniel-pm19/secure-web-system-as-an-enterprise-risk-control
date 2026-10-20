# Secure Web System as an Enterprise Risk Control

Web system with a React frontend, Auth0 authentication, a Spring Boot API (Java 21) protected as an OAuth 2.0 Resource Server, and public HTTPS access through Duck DNS, Apache HTTP Server, and a Let's Encrypt certificate.

**Deployed URL:** <https://daniel-risk-lab.duckdns.org>

![Authenticated API call from the HTTPS URL](docs/evidence/screenshots/17-https-api-200.png)

## Architecture

```
Browser
  │ login and access token
  ▼
Auth0 ───────────────► React (Vite) ── Authorization: Bearer <token> ──► Spring Boot API
                                                                         │ 127.0.0.1:8080
Internet ── Duck DNS ── Apache + TLS (80 → 443) ── ProxyPass /api/ ──────┘
```

| Component | Implementation |
| --- | --- |
| Server | AWS EC2 `t3.micro`, Amazon Linux 2023, public IP `54.221.66.193` |
| DNS | `daniel-risk-lab.duckdns.org` → the instance's public IP |
| Web and TLS | Apache 2.4 with `mod_ssl`, Let's Encrypt certificate (ISRG chain, TLS 1.3), and HTTP → HTTPS redirect |
| Frontend | Static Vite build in `/var/www/enterprise-risk-frontend` |
| Backend | Spring Boot 4.1 JAR running as the systemd service `enterprise-risk-api`, under the unprivileged user `riskapi`, listening only on `127.0.0.1:8080` |
| Identity | Auth0 tenant, API `https://enterprise-risk-api` (RS256) with the `read:risk` permission |

## Repository layout

- [`frontend/`](frontend/): React + Vite + `@auth0/auth0-react`; login, logout, and an API call with a Bearer token.
- [`backend/`](backend/): Spring Boot API; validates the RS256 signature, issuer, audience, expiration, and the `read:risk` permission.
- [`deploy/`](deploy/): Apache virtual host (Ubuntu and Amazon Linux), systemd unit, and server environment template.
- [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md): Auth0 configuration and deployment guide.
- [`docs/EVALUATION.md`](docs/EVALUATION.md): the assignment checklist with the evidence for each item.
- [`docs/evidence/`](docs/evidence/): command output and screenshots.

## Evidence

| Requirement | Evidence |
| --- | --- |
| Successful authenticated call (HTTPS) | [17-https-api-200.png](docs/evidence/screenshots/17-https-api-200.png) |
| Valid certificate | [certificate.txt](docs/evidence/certificate.txt) (`Verify return code: 0 (ok)`, issued by Let's Encrypt, expires 2027-01-18) |
| 401 without a token | [unauthenticated.txt](docs/evidence/unauthenticated.txt) |
| HTTP redirects to HTTPS | [http-redirect.txt](docs/evidence/http-redirect.txt) |
| Port 8080 unreachable from the internet | [port-8080.txt](docs/evidence/port-8080.txt) |
| Certificate renewal | [18-certbot-renew-dry-run.png](docs/evidence/screenshots/18-certbot-renew-dry-run.png) |
| Central service logs | [19-journalctl-service-logs.png](docs/evidence/screenshots/19-journalctl-service-logs.png) |
| Automated backend tests | [local-tests.txt](docs/evidence/local-tests.txt) (13 tests, 0 failures) |

<details>
<summary>Auth0 configuration</summary>

| | |
| --- | --- |
| API with identifier `https://enterprise-risk-api` | ![](docs/evidence/screenshots/02-auth0-api-create.png) |
| RS256 signing | ![](docs/evidence/screenshots/03-auth0-api-rs256.png) |
| `read:risk` permission | ![](docs/evidence/screenshots/05-auth0-permission-read-risk.png) |
| SPA with User-Delegated Access to the API | ![](docs/evidence/screenshots/06-auth0-spa-user-delegated-access.png) |
| Auth0 Universal Login | ![](docs/evidence/screenshots/07-auth0-universal-login.png) |

</details>

<details>
<summary>Infrastructure and HTTPS flow</summary>

| | |
| --- | --- |
| EC2 instance | ![](docs/evidence/screenshots/12-ec2-instance.png) |
| Duck DNS pointing to the instance | ![](docs/evidence/screenshots/13-duckdns-ec2-ip.png) |
| Home screen over HTTPS | ![](docs/evidence/screenshots/15-https-login.png) |
| Signed-in session over HTTPS | ![](docs/evidence/screenshots/16-https-session.png) |

</details>

<details>
<summary>Local flow</summary>

| | |
| --- | --- |
| Signed-in session on `localhost:5173` | ![](docs/evidence/screenshots/09-local-session.png) |
| 200 response locally | ![](docs/evidence/screenshots/10-local-api-200.png) |

</details>

## Running locally

Requirements: Java 21, Node 20.19+ or 22.12+, npm, and an Auth0 tenant configured as described in [DEPLOYMENT.md](docs/DEPLOYMENT.md).

1. Copy `frontend/.env.example` to `frontend/.env.local` and fill in the domain and Client ID.
2. Create `backend/.env` with `AUTH0_ISSUER_URI`, `AUTH0_JWK_SET_URI`, and `AUTH0_AUDIENCE` (`KEY=value` format, see [`deploy/backend.env.example`](deploy/backend.env.example)). Neither file is committed.
3. Backend:

```bash
cd backend
./mvnw spring-boot:run
```

4. Frontend, in another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. Without real Auth0 values, the page shows "Configuración pendiente" (configuration pending).

```bash
curl -i http://127.0.0.1:8080/api/risk-status   # 401
curl -i http://127.0.0.1:8080/actuator/health   # 200
```

## Tests and build

```bash
cd backend && ./mvnw clean verify
cd ../frontend && npm ci && npm run build
```

The integration tests start the API against a local JWKS server and JWTs signed with temporary RSA keys. They cover: no token (401), malformed token (401), valid token (200), missing `read:risk` permission (403), wrong issuer (401), wrong audience (401), expired token (401), invalid signature (401), public health endpoint (200), all other routes denied (403), and CORS (allowed origin 200, disallowed origin 403).

## Authentication vs. authorization

**Authentication** answers who the user is: Auth0 verifies the credentials and issues a signed access token. **Authorization** answers what the user may do: the API requires that token to carry the `read:risk` permission. That is why a missing or invalid token gets **401**, while a valid token without the permission gets **403**. The backend goes one step beyond the assignment, which only required `authenticated()`.

## Controls and risks

| Control | Risk reduced | Evidence |
| --- | --- | --- |
| Auth0 authentication | Impersonation and weak credential handling | Auth0 Universal Login; the password never passes through the application |
| JWT validation at the API | Unauthorized API access | 401 without a token, with an invalid signature, or when expired |
| Issuer and audience validation | Reuse of tokens from another tenant or meant for another API | Negative tests returning 401 |
| `read:risk` permission | Authenticated user without authorization for the operation | 403 without the permission, 200 with it |
| HTTPS with a valid certificate | Credential or token exposure in transit | `certificate.txt`, 301 redirect, and a successful renewal dry run |
| Apache reverse proxy + loopback binding | Direct exposure of the application port | Only 80 and 443 are public; 8080 times out from the internet |
| Central service logs | Delayed incident investigation | `journalctl -u enterprise-risk-api` with the `risk_status_read` event; access logs without tokens |

## Security notes

- The frontend only contains the domain, Client ID, and audience; there is no client secret.
- The Auth0 SDK keeps tokens in memory. The application does not display, log, or manually store them.
- CORS allows exactly `http://localhost:5173` and the production origin, and only the `GET` method.
- CSRF is disabled because the API uses no session or cookies; authentication is done with a Bearer token.
- The systemd service runs as a system user with `NoNewPrivileges`, `ProtectSystem=strict`, and `ProtectHome`.
- `.env*` files, keys, and certificates are listed in `.gitignore`.

## References

- [Auth0 React SDK](https://auth0.com/docs/libraries/auth0-react)
- [Spring Security: JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Certbot](https://eff-certbot.readthedocs.io/en/stable/using.html)


## Author

**Daniel Patiño Mejia**
