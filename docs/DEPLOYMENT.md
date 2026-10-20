# Configuración y despliegue

Guía reproducible del despliegue. Los valores `YOUR_*`, `SERVER_IP` y `DEPLOY_USER` son placeholders que deben sustituirse. Los comandos siguen el enunciado (Ubuntu con systemd).

## Despliegue realizado

| | |
| --- | --- |
| URL | <https://daniel-risk-lab.duckdns.org> |
| Servidor | AWS EC2 `t3.micro`, Amazon Linux 2023 (`us-east-1`), IP pública `54.221.66.193` |
| Apache | 2.4 con `mod_ssl`; virtual host [`deploy/enterprise-risk-amazon-linux.conf`](../deploy/enterprise-risk-amazon-linux.conf) |
| Backend | Unidad [`deploy/enterprise-risk-api.service`](../deploy/enterprise-risk-api.service), variables en `/etc/enterprise-risk-api.env` |
| Certificado | Let's Encrypt emitido con Certbot, válido hasta 2027-01-18, renovación de prueba exitosa |
| Red | Security group con 80 y 443 públicos; 8080 no expuesto |

Diferencias de Amazon Linux respecto a Ubuntu: los paquetes se instalan con `dnf`, el servicio se llama `httpd` en lugar de `apache2`, los módulos se cargan desde `/etc/httpd/conf.modules.d/` (no existe `a2enmod`/`a2ensite`), los virtual hosts van en `/etc/httpd/conf.d/` y los logs en `/var/log/httpd/`. El firewall se controla con el security group de EC2.

## 1. Acciones del estudiante: Auth0

1. Crea un tenant y una API **Enterprise Risk Lab API**, identificador `https://enterprise-risk-api`, firma RS256.
2. Agrega el permiso `read:risk` (Read the protected risk status).
3. Crea una SPA **Enterprise Risk Lab Frontend**. Configura Callback URLs, Logout URLs y Web Origins con `http://localhost:5173`; al desplegar agrega `https://YOUR-SUBDOMAIN.duckdns.org` en los tres campos, sin comodines.
4. En los permisos de acceso de la SPA a la API, concede **User-Delegated Access** y `read:risk`. El acceso máquina a máquina no sustituye esta concesión. Si activas RBAC, asigna también el permiso al usuario mediante un rol.
5. Crea un usuario de prueba en una conexión habilitada para la SPA. Ingresa su contraseña únicamente en Auth0.
6. Copia el dominio y Client ID a `.env.local`. El audience debe coincidir en frontend, backend y Auth0. No se necesita un client secret.
7. Configura en backend tanto el issuer (con `https://` y barra final) como la URL JWKS del mismo tenant.
8. Inicia ambos componentes y prueba login → consultar API → HTTP 200 → logout. Guarda una captura sin tokens.

## 2. Acciones del estudiante: servidor y DNS

En este despliegue se usó una instancia EC2 de AWS; cualquier servidor Linux propio sirve.

- Crea un servidor Linux con Java 21 disponible. Cambia credenciales predeterminadas y prepara un usuario de despliegue no root con acceso SSH por clave.
- Registra un subdominio en Duck DNS y apunta su registro a la IP pública del servidor. Si la IP cambia, configura el actualizador oficial con el token guardado de forma privada. Con IP reservada, verifica el registro cada vez que cambies el servidor.
- Verifica `dig +short YOUR-SUBDOMAIN.duckdns.org` y compara con la IP del servidor. Comprueba también que no haya un registro AAAA que apunte a otro servidor.
- Permite 80 y 443 públicamente en firewall y proveedor. No abras 8080. Para administrar, restringe SSH a tu IP o usa acceso privado del proveedor. Conserva la sesión y verifica el acceso administrativo antes de activar el firewall.

En Ubuntu:

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

No ejecutes el comando de SSH con el placeholder. Si usas gestión privada, adapta esa regla a dicho mecanismo.

## 3. Compilar y transferir desde desarrollo

Copia `frontend/.env.production.example` a `frontend/.env.production.local` y reemplaza los valores. Vite integra estas variables al compilar: cambiar un archivo después de compilar no cambia el sitio. Si existe `.env.local`, sus valores tienen precedencia menor que `.env.production.local`.

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

## 4. Instalar backend en el servidor

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

La escucha debe ser `127.0.0.1:8080`. Si el usuario ya existe, no repitas `useradd`. Reemplaza todos los placeholders del archivo de entorno antes de iniciar. Tras cambios: `sudo systemctl restart enterprise-risk-api`.

## 5. Apache y certificado

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

Sustituye ServerName antes de habilitar. Certbot solicitará correo y aceptación de términos: esas decisiones corresponden al estudiante. Certbot crea el virtual host TLS; comprueba que conserve el proxy `/api/`, las reglas SPA y las cabeceras. La plantilla determina X-Forwarded-Proto según la conexión TLS.

Actualiza los tres orígenes de Auth0 al hostname HTTPS definitivo. Verifica la URL en el navegador y consulta la API autenticado. No expongas `/actuator/health` mediante Apache; se consulta localmente.

## 6. Evidencias reales antes de entregar

Desde otra máquina (no desde el servidor):

```bash
curl -i https://YOUR-SUBDOMAIN.duckdns.org/api/risk-status
curl -I http://YOUR-SUBDOMAIN.duckdns.org
curl --connect-timeout 5 http://SERVER_IP:8080/api/risk-status
openssl s_client -connect YOUR-SUBDOMAIN.duckdns.org:443 -servername YOUR-SUBDOMAIN.duckdns.org -verify_hostname YOUR-SUBDOMAIN.duckdns.org -verify_return_error </dev/null
```

Esperado: 401, redirección a HTTPS, conexión rechazada o agotada en 8080 y `Verify return code: 0 (ok)` con nombre correcto. No uses `curl -k`.

Evidencias guardadas en `docs/evidence/`:

- `screenshots/17-https-api-200.png`: navegador en la URL HTTPS, sesión iniciada y respuesta HTTP 200 con `status: protected`.
- `certificate.txt`: certificado, hostname, vigencia y cadena válida.
- `unauthenticated.txt`: respuesta 401 pública.
- `http-redirect.txt`: redirección 301 de HTTP a HTTPS.
- `port-8080.txt`: comprobación externa del puerto 8080.
- `screenshots/18-certbot-renew-dry-run.png`: resultado de `sudo certbot renew --dry-run`.
- `screenshots/19-journalctl-service-logs.png`: arranque del servicio y evento `risk_status_read` en journalctl.

Revisa las evidencias para no incluir secretos. Las evidencias de este despliegue están en [`docs/evidence/`](evidence/) y enlazadas desde el [README](../README.md); la renovación y los logs del servicio están como capturas en [`screenshots/`](evidence/screenshots/).
