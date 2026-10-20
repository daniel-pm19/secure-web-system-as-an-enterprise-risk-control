# Verificación del laboratorio — 20 de octubre de 2026

Correspondencia entre los entregables y el checklist del enunciado y la evidencia que los respalda. Despliegue: <https://daniel-risk-lab.duckdns.org>.

## Entregables

- [x] Código fuente en [`frontend/`](../frontend/) y [`backend/`](../backend/).
- [x] [README](../README.md) con la URL HTTPS desplegada.
- [x] Captura de una llamada autenticada exitosa: [17-https-api-200.png](evidence/screenshots/17-https-api-200.png).
- [x] Salida del certificado válido: [certificate.txt](evidence/certificate.txt).

## Checklist de la Parte 7

| Punto | Estado | Evidencia |
| --- | :---: | --- |
| Las peticiones sin autenticación a `/api/risk-status` devuelven 401 | ✅ | [unauthenticated.txt](evidence/unauthenticated.txt), prueba `noTokenIsUnauthorized` |
| Un usuario autenticado puede llamar `/api/risk-status` desde el frontend | ✅ | [17-https-api-200.png](evidence/screenshots/17-https-api-200.png) |
| El backend valida emisor y audiencia del JWT | ✅ | Pruebas `wrongIssuerIsUnauthorized` y `wrongAudienceIsUnauthorized` en [local-tests.txt](evidence/local-tests.txt) |
| La aplicación está disponible en `https://daniel-risk-lab.duckdns.org` | ✅ | [15-https-login.png](evidence/screenshots/15-https-login.png), [http-redirect.txt](evidence/http-redirect.txt) |
| El navegador muestra un certificado válido | ✅ | [certificate.txt](evidence/certificate.txt): `Verify return code: 0 (ok)`, Let's Encrypt, TLS 1.3 |
| El puerto 8080 no es accesible desde Internet | ✅ | [port-8080.txt](evidence/port-8080.txt): la conexión agota el tiempo |
| `sudo certbot renew --dry-run` es exitoso | ✅ | [18-certbot-renew-dry-run.png](evidence/screenshots/18-certbot-renew-dry-run.png) |

## Configuración de Auth0 (Parte 1)

| Paso | Evidencia |
| --- | --- |
| API `Enterprise Risk Lab API`, identificador `https://enterprise-risk-api`, RS256 | [02](evidence/screenshots/02-auth0-api-create.png), [03](evidence/screenshots/03-auth0-api-rs256.png), [04](evidence/screenshots/04-auth0-api-created.png) |
| Permiso `read:risk` | [05](evidence/screenshots/05-auth0-permission-read-risk.png) |
| SPA `Enterprise Risk Lab Frontend` | [01](evidence/screenshots/01-auth0-spa-client.png) |
| User-Delegated Access de la SPA a la API | [06](evidence/screenshots/06-auth0-spa-user-delegated-access.png) |
| Login con usuario de prueba | [07](evidence/screenshots/07-auth0-universal-login.png), [16](evidence/screenshots/16-https-session.png) |

## Pruebas automatizadas del backend

`./mvnw clean verify` con Java 21: BUILD SUCCESS, 13 pruebas, 0 fallos ([local-tests.txt](evidence/local-tests.txt)).

| Caso | Resultado esperado |
| --- | --- |
| Sin token / token malformado | 401 |
| Token válido con `read:risk` | 200 |
| Token válido sin `read:risk` | 403 |
| Emisor incorrecto / audiencia incorrecta | 401 |
| Token vencido / firma con otra clave | 401 |
| `/actuator/health` sin token | 200 |
| Cualquier otra ruta | 403 |
| Preflight CORS desde origen permitido / no permitido | 200 / 403 |

Las pruebas usan un servidor JWKS local y claves RSA temporales; el flujo con el tenant real de Auth0 se verificó manualmente en local ([10-local-api-200.png](evidence/screenshots/10-local-api-200.png)) y en producción ([17-https-api-200.png](evidence/screenshots/17-https-api-200.png)).

## Auditoría

[19-journalctl-service-logs.png](evidence/screenshots/19-journalctl-service-logs.png) muestra el arranque del servicio `enterprise-risk-api` y el evento `event=risk_status_read outcome=success` de una consulta autorizada. Ni Apache ni Tomcat registran la cabecera `Authorization` ni la query string.
