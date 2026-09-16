# Checklist al reiniciar la sesión del AWS Academy Learner Lab

Cada vez que la sesión del lab expira (4 horas) las instancias EC2 se detienen y,
al volver a arrancarlas, **cambian sus IPs públicas**. Las privadas se mantienen.
Este es el orden exacto para dejar todo operativo de nuevo.

## 1. Arrancar el lab y las instancias

1. Canvas → Learner Lab → **Start Lab**, esperar el punto verde.
2. **EC2 → Instances**: si `ec2-apps` o `ec2-mq-kafka` están *stopped*, iniciarlas.
3. Anotar la **IP pública nueva de `ec2-apps`** y verificar que la **IP privada de
   `ec2-mq-kafka`** siga siendo `172.31.22.30`.
4. Descargar de nuevo el `labsuser.pem` desde el panel del lab.
5. Si SSH da *timeout* pero `https://<IP>:8085` responde, cambió tu IP pública:
   **EC2 → Security Groups → `seg-ssh` → Edit inbound rules**, en la regla SSH
   elegir Source **My IP** y guardar.

Los contenedores levantan solos gracias a `restart: unless-stopped`, no hace
falta volver a hacer `docker compose up`.

## 2. Si cambió la IP privada de `ec2-mq-kafka` (poco frecuente)

Solo en ese caso, actualizar en `~/infra/apps/.env` de `ec2-apps`:

```
RABBITMQ_HOST=<ip-privada-nueva>
KAFKA_BOOTSTRAP_SERVERS=<ip-privada-nueva>:9092
```

y en `~/kafka/docker-compose.yml` de `ec2-mq-kafka` el valor de
`KAFKA_ADVERTISED_HOST`. Después `docker compose up -d` en cada una.

## 3. Actualizar la IP pública de `ec2-apps` (siempre)

Con `<IP>` = la IP pública nueva:

### 3.1 API Gateway — 5 integraciones

**API Gateway → pedidos360-api → Integrations**, editar la URL de cada una:

| Integración | URL |
|---|---|
| orders (lista/creación) | `http://<IP>:8080/api/orders` |
| orders (detalle/estado) | `http://<IP>:8080/api/orders/{proxy}` |
| catalog | `http://<IP>:8081/api/catalog/{proxy}` |
| report | `http://<IP>:8083/api/report/{proxy}` |
| audit | `http://<IP>:8084/api/audit/{proxy}` |

El Invoke URL del Gateway **no cambia**, así que el proxy de Nginx no se toca.

### 3.2 Azure AD — redirect URI

**App registrations → Pedidos360 → Authentication → Single-page application**:
borrar el redirect URI viejo y agregar `https://<IP>:8085/auth/callback`.

### 3.3 API Gateway — origen de CORS

**API Gateway → pedidos360-api → CORS**: reemplazar Access-Control-Allow-Origin
por `https://<IP>:8085`. Presionar **Add** antes de guardar.

### 3.4 Backend — origen permitido

En `~/infra/apps/.env` de `ec2-apps`:

```
ALLOWED_ORIGINS=https://<IP>:8085,http://localhost:4200
```

Después `sudo docker compose up -d` para recrear los contenedores.

El frontend **no** necesita recompilarse: el `redirectUri` se calcula desde
`window.location.origin` y el Invoke URL del Gateway no cambia.

## 4. Verificación rápida

```bash
# Debe dar 401 (el Gateway valida y rechaza sin token)
curl -sk -o /dev/null -w "%{http_code}\n" https://<IP>:8085/api/orders

# Debe dar 200 (el frontend se sirve)
curl -sk -o /dev/null -w "%{http_code}\n" https://<IP>:8085/
```

Después, login en `https://<IP>:8085/` en una ventana de incógnito nueva.

## Recomendado: Elastic IP para `ec2-apps`

Asociar una Elastic IP (**EC2 → Elastic IPs → Allocate → Associate** a
`ec2-apps`) hace que la IP pública no cambie entre sesiones y elimina el paso 3
completo. Si el lab no permite asignarla, seguir con el checklist normal.

## Configuración que NO cambia entre sesiones

- Tenant ID: `2d0922c9-f3ff-4709-b9b7-0c4b54529639`
- Client ID: `d0120ca1-1d51-493b-9eaa-1121b5b7303a`
- Invoke URL del Gateway: `https://7x6u8dfoh0.execute-api.us-east-1.amazonaws.com`
- Endpoint de RDS y sus credenciales
- IP privada de `ec2-mq-kafka`: `172.31.22.30`

## Configuración crítica que costó encontrar (no tocar)

- **`requestedAccessTokenVersion: 2`** en el manifiesto de la App Registration.
  Con `null` Azure AD emite tokens v1, cuyo `iss` es `https://sts.windows.net/...`
  y no coincide con el issuer v2 que validan el Gateway y los microservicios.
- **Audiencias del JWT Authorizer**: deben ser dos entradas separadas,
  `api://<client-id>` y `<client-id>` a secas. Con tokens v2 el `aud` es el GUID.
- **`LoginComponent` pide el scope explícitamente** (`loginRedirect({ scopes })`).
  Sin eso MSAL solo pide los scopes OIDC y devuelve un token de Microsoft Graph,
  sin el claim `roles` y con una audiencia que nadie reconoce.
- **`handleRedirectObservable({ navigateToLoginRequestUrl: false })`** en el
  `APP_INITIALIZER`. Por defecto MSAL, tras procesar el login, recarga el
  navegador de vuelta a la página donde se llamó a `loginRedirect()` (`/login`),
  pisando la navegación al dashboard.
- **Nginx hace de proxy de `/api/*`** hacia el Gateway para que todo quede en el
  mismo origen. Las rutas del Gateway usan el método `ANY`, que incluye
  `OPTIONS`, y como tienen el authorizer attachado, el preflight de CORS (que va
  sin header `Authorization`) siempre respondía 401.
