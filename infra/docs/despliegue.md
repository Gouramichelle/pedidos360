# Guía de despliegue de Pedidos360

Cómo levantar el sistema completo desde cero: identidad en Azure AD,
infraestructura en AWS, microservicios y frontend.

Para volver a poner en marcha un entorno ya desplegado después de que expire la
sesión del laboratorio, ver [checklist-reinicio-lab.md](checklist-reinicio-lab.md).

## Arquitectura desplegada

```
Navegador
   │  HTTPS
   ▼
ec2-apps :8085  ── Nginx ──┬── sirve el frontend Angular (archivos estáticos)
                           │
                           └── proxy de /api/* ─────────┐
                                                        ▼
                                          API Gateway (HTTP API)
                                          JWT Authorizer de Azure AD
                                                        │
                        ┌───────────────────────────────┤
                        ▼                               ▼
              ec2-apps :8080-8084              (valida antes de enrutar)
              5 microservicios Spring Boot
              cada uno revalida el JWT
                        │
        ┌───────────────┼────────────────┐
        ▼               ▼                ▼
   RDS PostgreSQL   ec2-mq-kafka     ec2-mq-kafka
   (4 esquemas)     RabbitMQ :5672   Kafka :9092 + Zookeeper
```

El navegador nunca habla directo con los microservicios: todo pasa por Nginx y
de ahí por el API Gateway, que valida el token antes de enrutar.

## Puertos

| Puerto | Servicio | Instancia |
|---|---|---|
| 8080 | ms-orders | ec2-apps |
| 8081 | ms-catalog | ec2-apps |
| 8082 | ms-notify (sin API pública) | ec2-apps |
| 8083 | ms-report | ec2-apps |
| 8084 | ms-audit | ec2-apps |
| 8085 | Nginx: frontend + proxy de la API | ec2-apps |
| 5672 / 15672 | RabbitMQ y su consola | ec2-mq-kafka |
| 9092 / 2181 | Kafka y Zookeeper | ec2-mq-kafka |
| 5432 | PostgreSQL | RDS |

---

## Parte 1 — Identidad en Azure AD

Se hace una sola vez y no depende de AWS.

### 1.1 Tenant y App Registration

1. En [portal.azure.com](https://portal.azure.com), crear un tenant de Microsoft
   Entra ID (o usar uno existente).
2. **App registrations → New registration**:
   - Name: `Pedidos360`
   - Supported account types: *Accounts in this organizational directory only*
   - Redirect URI: plataforma **Single-page application**, valor
     `http://localhost:4200/auth/callback`
3. Anotar de la pantalla Overview el **Application (client) ID** y el
   **Directory (tenant) ID**.

### 1.2 Exponer la API

En **Expose an API**:

1. **Add** junto a Application ID URI, aceptando `api://<client-id>`.
2. **Add a scope**: nombre `access_as_user`, consentimiento de *Admins and
   users*, estado *Enabled*.

### 1.3 Roles de aplicación

En **App roles**, crear cuatro. El campo **Value** debe coincidir exactamente,
respetando mayúsculas, porque es lo que viaja en el claim `roles` del token y lo
que evalúan las anotaciones `@PreAuthorize` del backend:

| Display name | Value |
|---|---|
| Admin | `Admin` |
| Operador | `Operador` |
| Cliente | `Cliente` |
| Auditor | `Auditor` |

### 1.4 Tokens versión 2 (paso obligatorio)

En **Manifest**, dentro del objeto `api`, cambiar:

```json
"requestedAccessTokenVersion": 2
```

Sin esto Azure AD emite tokens v1, cuyo `iss` es `https://sts.windows.net/...`,
que no coincide con el issuer v2 configurado en el API Gateway y en los
microservicios. El síntoma es un 401 en todas las llamadas pese a haber iniciado
sesión correctamente.

### 1.5 Permisos y consentimiento

En **API permissions**: *Add a permission → My APIs → Pedidos360 → Delegated →
`access_as_user`*, y después **Grant admin consent**. Esto evita que a cada
usuario, y sobre todo a los invitados, le aparezca una pantalla de aprobación.

### 1.6 Asignar usuarios a roles

En **Enterprise applications → Pedidos360 → Users and groups → Add user/group**,
asignar cada usuario a un rol. Un mismo usuario puede tener varios roles, pero
hay que agregarlo una vez por cada uno.

Para usuarios de otra organización: **Microsoft Entra ID → Users → Invite
external user**. El invitado puede aceptar la invitación simplemente iniciando
sesión en la aplicación, no necesita el correo.

---

## Parte 2 — Infraestructura en AWS

### 2.1 Red

Se usa la VPC por defecto de la región, sin crear nada. Verificar en **VPC →
Subnets** que las subredes tengan habilitada la asignación automática de IP
pública.

### 2.2 Security Groups

Crear cinco en **EC2 → Security Groups**. AWS reserva el prefijo `sg-` para los
identificadores internos, así que los nombres usan `seg-`:

| Nombre | Reglas de entrada |
|---|---|
| `seg-ssh` | SSH (22) desde *My IP* |
| `seg-apps` | TCP 8080-8085 desde `0.0.0.0/0` |
| `seg-mq` | TCP 5672 y 15672 desde `seg-apps` |
| `seg-kafka` | TCP 9092 desde `seg-apps`, TCP 2181 desde sí mismo |
| `seg-rds` | PostgreSQL (5432) desde `seg-apps` |

`seg-kafka` se referencia a sí mismo, así que hay que crearlo primero con la
regla de 9092 y agregar después la de 2181.

### 2.3 Base de datos

En **RDS → Create database**:

- Engine PostgreSQL, plantilla **Free tier** (evita que la consola proponga un
  clúster Multi-AZ con almacenamiento provisionado, que es caro e innecesario).
- Identificador `pedidos360-db`, clase `db.t3.micro`.
- Credentials management: **Self managed**, con usuario `pedidos360_app` y una
  contraseña propia.
- Public access: **No**. VPC security group: `seg-rds`.
- En Additional configuration, **Initial database name**: `pedidos360`.

Los cuatro microservicios con persistencia comparten esta instancia y esta base,
separados por esquema (`orders`, `catalog`, `report`, `audit`). Hibernate crea
los esquemas y las tablas al arrancar.

### 2.4 Instancias EC2

Lanzar dos, ambas con Amazon Linux 2023 y el key pair `vockey` del laboratorio:

| Nombre | Tipo | Security groups |
|---|---|---|
| `ec2-apps` | `t3.medium` | `seg-ssh`, `seg-apps` |
| `ec2-mq-kafka` | `t3.small` | `seg-ssh`, `seg-mq`, `seg-kafka` |

El tamaño de `ec2-apps` importa: corre cinco JVM más Nginx, y con 2 GB de RAM el
sistema entra en falta de memoria y las compilaciones lo dejan sin responder.

En **Advanced details → User data** de ambas, para instalar Docker:

```bash
#!/bin/bash
dnf update -y
dnf install -y docker
systemctl enable docker
systemctl start docker
usermod -aG docker ec2-user

mkdir -p /usr/local/lib/docker/cli-plugins
curl -fSL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose || exit 1
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
```

Amazon Linux 2023 trae una versión de buildx anterior a la que exige
`docker compose build`. En `ec2-apps` hay que actualizarla:

```bash
VER=$(curl -s https://api.github.com/repos/docker/buildx/releases/latest | grep -o '"tag_name": "[^"]*"' | cut -d'"' -f4)
sudo curl -fSL "https://github.com/docker/buildx/releases/download/${VER}/buildx-${VER}.linux-amd64" \
  -o /usr/local/lib/docker/cli-plugins/docker-buildx
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx
```

---

## Parte 3 — Desplegar la mensajería

En `ec2-mq-kafka`, copiar `infra/mq` e `infra/kafka` y levantarlos:

```bash
scp -i labsuser.pem -r infra/mq infra/kafka ec2-user@<ip-publica-mq>:~/
ssh -i labsuser.pem ec2-user@<ip-publica-mq>
cd ~/mq    && sudo docker compose up -d
cd ~/kafka && sudo docker compose up -d
```

En `infra/kafka/docker-compose.yml`, `KAFKA_ADVERTISED_HOST` tiene que ser la
**IP privada** de esta instancia. Es la dirección que Kafka le anuncia a los
clientes, y los microservicios corren en otra instancia de la misma VPC. Si se
deja `localhost`, los consumidores se conectan pero nunca reciben mensajes.

Los tópicos `orders.events`, `audit.timeline` y los `.DLT` los crean los propios
microservicios al arrancar, porque la creación automática está deshabilitada en
el broker.

---

## Parte 4 — Desplegar microservicios y frontend

En `ec2-apps`, copiar el repositorio y crear el archivo de configuración:

```bash
scp -i labsuser.pem -r ms-pedidos360-* frontend-pedidos360 infra ec2-user@<ip-publica-apps>:~/
ssh -i labsuser.pem ec2-user@<ip-publica-apps>
cd ~/infra/apps
cp ../../ms-pedidos360-orders/.env.example .env   # y completar
```

El `.env` debe quedar así:

```bash
DB_HOST=pedidos360-db.xxxxx.us-east-1.rds.amazonaws.com
DB_PORT=5432
DB_NAME=pedidos360
DB_USER=pedidos360_app
DB_PASSWORD="la-contraseña"      # entre comillas si tiene # u otros símbolos
DDL_AUTO=update

RABBITMQ_HOST=<ip-privada-de-ec2-mq-kafka>
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest
KAFKA_BOOTSTRAP_SERVERS=<ip-privada-de-ec2-mq-kafka>:9092

AZURE_ISSUER_URI=https://login.microsoftonline.com/<tenant-id>/v2.0
AZURE_AUDIENCES=api://<client-id>,<client-id>

ALLOWED_ORIGINS=http://localhost:4200
```

Las comillas en la contraseña no son opcionales si contiene `#`: Docker Compose
interpreta ese carácter como inicio de comentario y trunca el valor.

`AZURE_AUDIENCES` lleva los dos formatos porque el claim `aud` es
`api://<client-id>` en tokens v1 y el GUID pelado en tokens v2.

Construir y levantar todo:

```bash
sudo docker compose build
sudo docker compose up -d
sudo docker compose ps
```

Las imágenes se construyen en la instancia con un Dockerfile multi-etapa: Maven
compila y la imagen final solo lleva el JRE. La primera construcción tarda
varios minutos porque descarga las dependencias.

---

## Parte 5 — API Gateway

En **API Gateway → Create API → HTTP API**, nombre `pedidos360-api`.

### 5.1 Integraciones

Cinco, todas de tipo **HTTP URI** con método `ANY`, apuntando a la IP pública de
`ec2-apps`:

| Integración | URL |
|---|---|
| orders (listado y creación) | `http://<ip-apps>:8080/api/orders` |
| orders (detalle y estados) | `http://<ip-apps>:8080/api/orders/{proxy}` |
| catalog | `http://<ip-apps>:8081/api/catalog/{proxy}` |
| report | `http://<ip-apps>:8083/api/report/{proxy}` |
| audit | `http://<ip-apps>:8084/api/audit/{proxy}` |

Orders necesita dos porque `{proxy+}` exige al menos un segmento después de la
ruta: sin la primera integración, `GET /api/orders` y `POST /api/orders`
devuelven 404.

### 5.2 Rutas

Cinco rutas con método `ANY`, cada una asociada a su integración:

```
/api/orders
/api/orders/{proxy+}
/api/catalog/{proxy+}
/api/report/{proxy+}
/api/audit/{proxy+}
```

### 5.3 Authorizer

En **Authorization → Create and attach an authorizer**, tipo **JWT**:

- Name: `azure-ad-jwt`
- Identity source: `$request.header.Authorization`
- Issuer: `https://login.microsoftonline.com/<tenant-id>/v2.0`
- Audience: dos entradas **separadas**, `api://<client-id>` y `<client-id>`

Hay que adjuntarlo a las cinco rutas: no se aplica solo.

### 5.4 Stage

El stage `$default` se crea con **Auto-deploy** activado, así que cada cambio se
publica solo. Anotar el **Invoke URL**, que no cambia aunque cambien las IPs de
las instancias.

### 5.5 Conectar el proxy

En `frontend-pedidos360/nginx.conf`, el bloque `location /api/` debe apuntar al
Invoke URL. Es el único lugar donde aparece:

```nginx
proxy_pass https://<invoke-url-sin-https>/api/;
proxy_set_header Host <invoke-url-sin-https>;
```

El header `Host` y `proxy_ssl_server_name on` son obligatorios: API Gateway
enruta por nombre de host y exige SNI en el handshake TLS.

Este proxy existe para que el navegador vea todo en el mismo origen. Las rutas
del Gateway usan el método `ANY`, que incluye `OPTIONS`, y como tienen el
authorizer adjunto, el preflight de CORS, que el navegador envía sin cabecera
`Authorization`, siempre respondía 401.

### 5.6 Registrar el frontend en Azure AD

Agregar `https://<ip-apps>:8085/auth/callback` como redirect URI de tipo
Single-page application. Azure AD exige `https` salvo en localhost, por eso
Nginx sirve con un certificado autofirmado que se genera durante la
construcción de la imagen. El navegador va a pedir aceptar la advertencia la
primera vez.

---

## Parte 6 — Verificación

```bash
# El frontend responde
curl -sk -o /dev/null -w "%{http_code}\n" https://<ip-apps>:8085/          # 200

# El Gateway valida y rechaza sin token
curl -sk -o /dev/null -w "%{http_code}\n" https://<ip-apps>:8085/api/orders # 401

# Con un token inválido también rechaza
curl -sk -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer falso" https://<ip-apps>:8085/api/orders        # 401
```

Después, iniciar sesión en `https://<ip-apps>:8085/` y recorrer el flujo: crear
un pedido, llevarlo por todos sus estados y revisar reportes y auditoría.

Para confirmar la mensajería, en `ec2-apps`:

```bash
cd ~/infra/apps
sudo docker compose logs ms-orders | grep -E "Publicado|Encolado"
sudo docker compose logs ms-notify | grep "Enviando email"
sudo docker compose logs ms-audit  | grep "Auditado"
sudo docker compose logs ms-report | grep "Snapshot"
```

Cada evento conserva el mismo `correlationId` a lo largo de los cuatro
servicios, lo que permite seguir una operación de punta a punta.

---

## Ejecución local, sin AWS

Para desarrollar sin desplegar nada:

```bash
docker compose -f infra/mq/docker-compose.yml up -d
docker compose -f infra/kafka/docker-compose.yml up -d   # KAFKA_ADVERTISED_HOST=localhost
```

Con un PostgreSQL local y la base `pedidos360` creada, cada microservicio se
levanta con `mvn spring-boot:run` tomando su `.env`. El frontend con `npm start`
usa `environment.ts`, que apunta a cada microservicio en localhost en vez de
pasar por el API Gateway.

La documentación de cada API queda en `http://localhost:<puerto>/swagger-ui.html`.
