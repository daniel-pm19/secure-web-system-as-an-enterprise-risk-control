# Configuration and deployment

Reproducible deployment guide. `YOUR_*`, `SERVER_IP`, and `DEPLOY_USER` are placeholders that must be replaced. The commands follow the assignment (Ubuntu with systemd).

## Actual deployment

| | |
| --- | --- |
| URL | <https://daniel-risk-lab.duckdns.org> |
| Server | AWS EC2 `t3.micro`, Amazon Linux 2023 (`us-east-1`), public IP `54.221.66.193` |
| Apache | 2.4 with `mod_ssl`; virtual host [`deploy/enterprise-risk-amazon-linux.conf`](../deploy/enterprise-risk-amazon-linux.conf) |
| Backend | Unit [`deploy/enterprise-risk-api.service`](../deploy/enterprise-risk-api.service), variables in `/etc/enterprise-risk-api.env` |
| Certificate | Let's Encrypt, issued with Certbot, valid until 2027-01-18, successful renewal dry run |
| Network | Security group with 80 and 443 public; 8080 not exposed |

Differences between Amazon Linux and Ubuntu: packages are installed with `dnf`, the service is called `httpd` instead of `apache2`, modules are loaded from `/etc/httpd/conf.modules.d/` (there is no `a2enmod`/`a2ensite`), virtual hosts go in `/etc/httpd/conf.d/`, and logs are in `/var/log/httpd/`. The firewall is managed through the EC2 security group.

## 1. Auth0 setup

1. Create a tenant and an API named **Enterprise Risk Lab API**, identifier `https://enterprise-risk-api`, RS256 signing.
2. Add the `read:risk` permission (Read the protected risk status).
3. Create a SPA named **Enterprise Risk Lab Frontend**. Set Callback URLs, Logout URLs, and Web Origins to `http://localhost:5173`; when deploying, add `https://YOUR-SUBDOMAIN.duckdns.org` to all three fields, without wildcards.
4. In the SPA's API access settings, grant **User-Delegated Access** with `read:risk`. Machine-to-machine access does not replace this grant. If you enable RBAC, also assign the permission to the user through a role.
5. Create a test user in a connection enabled for the SPA. Enter its password only in Auth0.
6. Copy the domain and Client ID into `.env.local`. The audience must match in the frontend, the backend, and Auth0. No client secret is needed.
7. In the backend, configure both the issuer (with `https://` and the trailing slash) and the JWKS URL of the same tenant.
8. Start both components and test login → call API → HTTP 200 → logout. Take a screenshot that shows no tokens.

## 2. Server and DNS

This deployment used an AWS EC2 instance; any Linux server you control works.

- Create a Linux server with Java 21. Change default credentials and set up a non-root deployment user with key-based SSH access.
- Register a Duck DNS subdomain and point it to the server's public IP. If the IP changes, configure the official updater with the token stored privately. With a reserved IP, check the record every time you change the server.
- Run `dig +short YOUR-SUBDOMAIN.duckdns.org` and compare it with the server IP. Also check that no AAAA record points to another server.
- Allow 80 and 443 publicly in the firewall and at the provider. Do not open 8080. For administration, restrict SSH to your IP or use the provider's private access. Keep your session open and verify administrative access before enabling the firewall.

On Ubuntu:

```bash
sudo apt update
sudo apt install -y apache2 certbot python3-certbot-apache openjdk-21-jre ufw
sudo ufw allow from YOUR_ADMIN_IP to any port 22 proto tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw enable
sudo ufw status verbose
```

Do not run the SSH rule with the placeholder. If you use private management access, adapt that rule accordingly.

## 3. Build and transfer from the development machine

Copy `frontend/.env.production.example` to `frontend/.env.production.local` and replace the values. Vite embeds these variables at build time: changing a file after the build does not change the site. If `.env.local` exists, its values have lower precedence than `.env.production.local`.

```bash
cd backend
./mvnw clean verify
cd ../frontend
npm ci
npm run build
cd ..
ssh DEPLOY_USER@SERVER_IP 'mkdir -p ~/risk-release/frontend ~/risk-release/deploy'
scp backend/target/secure-web-system-as-an-enterprise-risk-control-0.0.1-SNAPSHOT.jar DEPLOY_USER@SERVER_IP:~/risk-release/app.jar
scp -r frontend/dist/. DEPLOY_USER@SERVER_IP:~/risk-release/frontend/
scp deploy/enterprise-risk-api.service deploy/enterprise-risk.conf deploy/backend.env.example DEPLOY_USER@SERVER_IP:~/risk-release/deploy/
```

## 4. Install the backend on the server

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin riskapi
sudo install -d -o root -g riskapi -m 750 /opt/enterprise-risk-api
sudo install -o root -g riskapi -m 640 ~/risk-release/app.jar /opt/enterprise-risk-api/app.jar
sudo install -o root -g root -m 600 ~/risk-release/deploy/backend.env.example /etc/enterprise-risk-api.env
sudoedit /etc/enterprise-risk-api.env
sudo install -m 644 ~/risk-release/deploy/enterprise-risk-api.service /etc/systemd/system/enterprise-risk-api.service
sudo systemctl daemon-reload
sudo systemctl enable --now enterprise-risk-api
curl -i http://127.0.0.1:8080/actuator/health
sudo ss -ltnp 'sport = :8080'
sudo journalctl -u enterprise-risk-api -n 50 --no-pager
```

The service must listen on `127.0.0.1:8080`. If the user already exists, skip `useradd`. Replace every placeholder in the environment file before starting. After changes: `sudo systemctl restart enterprise-risk-api`.

## 5. Apache and certificate

```bash
sudo install -d -m 755 /var/www/enterprise-risk-frontend
sudo cp -r ~/risk-release/frontend/. /var/www/enterprise-risk-frontend/
sudo a2enmod ssl proxy proxy_http rewrite headers
sudo install -m 644 ~/risk-release/deploy/enterprise-risk.conf /etc/apache2/sites-available/enterprise-risk.conf
sudoedit /etc/apache2/sites-available/enterprise-risk.conf
sudo a2ensite enterprise-risk.conf
sudo apachectl configtest
sudo systemctl reload apache2
sudo certbot --apache --redirect -d YOUR-SUBDOMAIN.duckdns.org
sudo certbot renew --dry-run
systemctl list-timers --all | grep certbot
```

Replace `ServerName` before enabling the site. Certbot asks for an email address and acceptance of its terms. Certbot creates the TLS virtual host; check that it keeps the `/api/` proxy, the SPA rules, and the headers. The template sets `X-Forwarded-Proto` according to the TLS connection.

Update the three Auth0 origins to the final HTTPS hostname. Check the URL in the browser and call the API while signed in. Do not expose `/actuator/health` through Apache; it is queried locally.

## 6. Collecting evidence

From another machine (not from the server):

```bash
curl -i https://YOUR-SUBDOMAIN.duckdns.org/api/risk-status
curl -I http://YOUR-SUBDOMAIN.duckdns.org
curl --connect-timeout 5 http://SERVER_IP:8080/api/risk-status
openssl s_client -connect YOUR-SUBDOMAIN.duckdns.org:443 -servername YOUR-SUBDOMAIN.duckdns.org -verify_hostname YOUR-SUBDOMAIN.duckdns.org -verify_return_error </dev/null
```

Expected: 401, a redirect to HTTPS, a refused or timed-out connection on 8080, and `Verify return code: 0 (ok)` with the correct hostname. Do not use `curl -k`.

Evidence stored in `docs/evidence/`:

- `screenshots/17-https-api-200.png`: browser on the HTTPS URL, signed in, with an HTTP 200 response showing `status: protected`.
- `certificate.txt`: certificate, hostname, validity period, and valid chain.
- `unauthenticated.txt`: public 401 response.
- `http-redirect.txt`: 301 redirect from HTTP to HTTPS.
- `port-8080.txt`: external check of port 8080.
- `screenshots/18-certbot-renew-dry-run.png`: output of `sudo certbot renew --dry-run`.
- `screenshots/19-journalctl-service-logs.png`: service startup and the `risk_status_read` event in journalctl.

Review the evidence so it contains no secrets. All evidence for this deployment is in [`docs/evidence/`](evidence/) and linked from the [README](../README.md).
