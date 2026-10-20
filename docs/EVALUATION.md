# Lab verification — October 20, 2026

Maps the assignment's deliverables and checklist to the evidence that supports each item. Deployment: <https://daniel-risk-lab.duckdns.org>.

## Deliverables

- [x] Source code in [`frontend/`](../frontend/) and [`backend/`](../backend/).
- [x] [README](../README.md) with the deployed HTTPS URL.
- [x] Screenshot of a successful authenticated call: [17-https-api-200.png](evidence/screenshots/17-https-api-200.png).
- [x] Output showing the valid certificate: [certificate.txt](evidence/certificate.txt).

## Part 7 checklist

| Item | Status | Evidence |
| --- | :---: | --- |
| Unauthenticated requests to `/api/risk-status` return 401 | ✅ | [unauthenticated.txt](evidence/unauthenticated.txt), test `noTokenIsUnauthorized` |
| A signed-in user can call `/api/risk-status` from the frontend | ✅ | [17-https-api-200.png](evidence/screenshots/17-https-api-200.png) |
| The backend validates the JWT issuer and audience | ✅ | Tests `wrongIssuerIsUnauthorized` and `wrongAudienceIsUnauthorized` in [local-tests.txt](evidence/local-tests.txt) |
| The application is available at `https://daniel-risk-lab.duckdns.org` | ✅ | [15-https-login.png](evidence/screenshots/15-https-login.png), [http-redirect.txt](evidence/http-redirect.txt) |
| The browser shows a valid certificate | ✅ | [certificate.txt](evidence/certificate.txt): `Verify return code: 0 (ok)`, Let's Encrypt, TLS 1.3 |
| Port 8080 is not reachable from the public internet | ✅ | [port-8080.txt](evidence/port-8080.txt): the connection times out |
| `sudo certbot renew --dry-run` succeeds | ✅ | [18-certbot-renew-dry-run.png](evidence/screenshots/18-certbot-renew-dry-run.png) |

## Auth0 configuration (Part 1)

| Step | Evidence |
| --- | --- |
| API `Enterprise Risk Lab API`, identifier `https://enterprise-risk-api`, RS256 | [02](evidence/screenshots/02-auth0-api-create.png), [03](evidence/screenshots/03-auth0-api-rs256.png), [04](evidence/screenshots/04-auth0-api-created.png) |
| `read:risk` permission | [05](evidence/screenshots/05-auth0-permission-read-risk.png) |
| SPA `Enterprise Risk Lab Frontend` | [01](evidence/screenshots/01-auth0-spa-client.png) |
| User-Delegated Access from the SPA to the API | [06](evidence/screenshots/06-auth0-spa-user-delegated-access.png) |
| Login with the test user | [07](evidence/screenshots/07-auth0-universal-login.png), [16](evidence/screenshots/16-https-session.png) |

## Automated backend tests

`./mvnw clean verify` with Java 21: BUILD SUCCESS, 13 tests, 0 failures ([local-tests.txt](evidence/local-tests.txt)).

| Case | Expected result |
| --- | --- |
| No token / malformed token | 401 |
| Valid token with `read:risk` | 200 |
| Valid token without `read:risk` | 403 |
| Wrong issuer / wrong audience | 401 |
| Expired token / signed with another key | 401 |
| `/actuator/health` without a token | 200 |
| Any other route | 403 |
| CORS preflight from an allowed / disallowed origin | 200 / 403 |

The tests use a local JWKS server and temporary RSA keys; the flow against the real Auth0 tenant was verified manually both locally ([10-local-api-200.png](evidence/screenshots/10-local-api-200.png)) and in production ([17-https-api-200.png](evidence/screenshots/17-https-api-200.png)).

## Audit

[19-journalctl-service-logs.png](evidence/screenshots/19-journalctl-service-logs.png) shows the `enterprise-risk-api` service starting and the `event=risk_status_read outcome=success` event for an authorized request. Neither Apache nor Tomcat logs the `Authorization` header or the query string.
