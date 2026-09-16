# Pedidos360 — EP1 (DSY1107)

Monorepo con los 6 componentes del encargo: el frontend Angular, los 5
microservicios Spring Boot y la infraestructura de despliegue.

```
frontend-pedidos360/     Angular 18 + MSAL
ms-pedidos360-orders/    Spring Boot 3 — pedidos, maquina de estados, productor Kafka/RabbitMQ
ms-pedidos360-catalog/   Spring Boot 3 — catalogo de productos y stock
ms-pedidos360-notify/    Spring Boot 3 — consumer RabbitMQ, sin DB, sin API publica
ms-pedidos360-report/    Spring Boot 3 — consumer Kafka, KPIs de solo lectura
ms-pedidos360-audit/     Spring Boot 3 — consumer Kafka, auditoria de solo lectura
infra/                   docker-compose para apps, RabbitMQ y Kafka+Zookeeper
```

## Arquitectura desplegada

```
Navegador
   │
   ├── HTTPS ──> ec2-apps :8085 (Nginx)  sirve el frontend Angular
   │
   └── HTTPS ──> API Gateway (HTTP API)  llamadas a /api/*
                 JWT Authorizer de Azure AD
                 CORS configurado para el origen del frontend
                         │  (valida el token antes de enrutar)
                         ▼
                 ec2-apps :8080-8084
                 5 microservicios Spring Boot
                 cada uno revalida el JWT
                         │
         ┌───────────────┼────────────────┐
         ▼               ▼                ▼
    RDS PostgreSQL   ec2-mq-kafka     ec2-mq-kafka
    (4 esquemas)     RabbitMQ :5672   Kafka :9092 + Zookeeper
```

El navegador nunca habla directo con los microservicios: las llamadas a la API
van al API Gateway, que valida el token antes de enrutar. Nginx solo sirve los
archivos del frontend.

Nginx conserva ademas un proxy de `/api/*` hacia el mismo Invoke URL, que queda
como alternativa de respaldo: apuntando `environment.prod.ts` a rutas relativas,
todo vuelve a viajar por el mismo origen y CORS deja de intervenir.

| Puerto | Servicio | Instancia |
|---|---|---|
| 8080 | ms-orders | ec2-apps |
| 8081 | ms-catalog | ec2-apps |
| 8082 | ms-notify (sin API publica) | ec2-apps |
| 8083 | ms-report | ec2-apps |
| 8084 | ms-audit | ec2-apps |
| 8085 | Nginx: frontend (y proxy de la API como respaldo) | ec2-apps |
| 5672 / 15672 | RabbitMQ y su consola | ec2-mq-kafka |
| 9092 / 2181 | Kafka y Zookeeper | ec2-mq-kafka |
| 5432 | PostgreSQL | RDS |

---

# Despliegue

Procedimiento completo desde cero. Para volver a poner en marcha un entorno ya
desplegado despues de que expire la sesion del laboratorio, ver
[infra/docs/checklist-reinicio-lab.md](infra/docs/checklist-reinicio-lab.md).

## 1. Identidad en Azure AD

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

### 1.3 Roles de aplicacion

En **App roles**, crear cuatro. El campo **Value** debe coincidir exactamente,
respetando mayusculas, porque es lo que viaja en el claim `roles` del token y lo
que evaluan las anotaciones `@PreAuthorize` del backend:

| Display name | Value |
|---|---|
| Admin | `Admin` |
| Operador | `Operador` |
| Cliente | `Cliente` |
| Auditor | `Auditor` |

### 1.4 Tokens version 2 (paso obligatorio)

En **Manifest**, dentro del objeto `api`, cambiar:

```json
"requestedAccessTokenVersion": 2
```

Sin esto Azure AD emite tokens v1, cuyo `iss` es `https://sts.windows.net/...`,
que no coincide con el issuer v2 configurado en el API Gateway y en los
microservicios. El sintoma es un 401 en todas las llamadas pese a haber iniciado
sesion correctamente.

### 1.5 Permisos y consentimiento

En **API permissions**: *Add a permission → My APIs → Pedidos360 → Delegated →
`access_as_user`*, y despues **Grant admin consent**. Esto evita que a cada
usuario, y sobre todo a los invitados, le aparezca una pantalla de aprobacion.

### 1.6 Asignar usuarios a roles

En **Enterprise applications → Pedidos360 → Users and groups → Add user/group**,
asignar cada usuario a un rol. Un mismo usuario puede tener varios roles, pero
hay que agregarlo una vez por cada uno.

Para usuarios de otra organizacion: **Microsoft Entra ID → Users → Invite
external user**. El invitado puede aceptar la invitacion simplemente iniciando
sesion en la aplicacion, no necesita el correo.

## 2. Red y Security Groups en AWS

Se usa la VPC por defecto de la region, sin crear nada. Verificar en **VPC →
Subnets** que las subredes tengan habilitada la asignacion automatica de IP
publica.

Crear cinco Security Groups en **EC2 → Security Groups**. AWS reserva el prefijo
`sg-` para los identificadores internos, asi que los nombres usan `seg-`:

| Nombre | Reglas de entrada |
|---|---|
| `seg-ssh` | SSH (22) desde *My IP* |
| `seg-apps` | TCP 8080-8085 desde `0.0.0.0/0` |
| `seg-mq` | TCP 5672 y 15672 desde `seg-apps` |
| `seg-kafka` | TCP 9092 desde `seg-apps`, TCP 2181 desde si mismo |
| `seg-rds` | PostgreSQL (5432) desde `seg-apps` |

`seg-kafka` se referencia a si mismo, asi que hay que crearlo primero con la
regla de 9092 y agregar despues la de 2181.

## 3. Base de datos

En **RDS → Create database**:

- Engine PostgreSQL, plantilla **Free tier** (evita que la consola proponga un
  cluster Multi-AZ con almacenamiento provisionado, que es caro e innecesario).
- Identificador `pedidos360-db`, clase `db.t3.micro`.
- Credentials management: **Self managed**, con usuario `pedidos360_app` y una
  contrasena propia.
- Public access: **No**. VPC security group: `seg-rds`.
- En Additional configuration, **Initial database name**: `pedidos360`.

Los cuatro microservicios con persistencia comparten esta instancia y esta base,
separados por esquema (`orders`, `catalog`, `report`, `audit`). Hibernate crea
los esquemas y las tablas al arrancar.

## 4. Instancias EC2

Lanzar dos, ambas con Amazon Linux 2023 y el key pair del laboratorio:

| Nombre | Tipo | Security groups |
|---|---|---|
| `ec2-apps` | `t3.medium` | `seg-ssh`, `seg-apps` |
| `ec2-mq-kafka` | `t3.small` | `seg-ssh`, `seg-mq`, `seg-kafka` |

El tamano de `ec2-apps` importa: corre cinco JVM mas Nginx, y con 2 GB de RAM el
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

Amazon Linux 2023 trae una version de buildx anterior a la que exige
`docker compose build`. En `ec2-apps` hay que actualizarla:

```bash
VER=$(curl -s https://api.github.com/repos/docker/buildx/releases/latest | grep -o '"tag_name": "[^"]*"' | cut -d'"' -f4)
sudo curl -fSL "https://github.com/docker/buildx/releases/download/${VER}/buildx-${VER}.linux-amd64" \
  -o /usr/local/lib/docker/cli-plugins/docker-buildx
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx
```

## 5. Mensajeria

En `ec2-mq-kafka`, copiar `infra/mq` e `infra/kafka` y levantarlos:

```bash
scp -i labsuser.pem -r infra/mq infra/kafka ec2-user@<ip-publica-mq>:~/
ssh -i labsuser.pem ec2-user@<ip-publica-mq>
cd ~/mq    && sudo docker compose up -d
cd ~/kafka && sudo docker compose up -d
```

En `infra/kafka/docker-compose.yml`, `KAFKA_ADVERTISED_HOST` tiene que ser la
**IP privada** de esta instancia. Es la direccion que Kafka le anuncia a los
clientes, y los microservicios corren en otra instancia de la misma VPC. Si se
deja `localhost`, los consumidores se conectan pero nunca reciben mensajes.

Los topicos `orders.events`, `audit.timeline` y los `.DLT` los crean los propios
microservicios al arrancar, porque la creacion automatica esta deshabilitada en
el broker.

## 6. Microservicios y frontend

En `ec2-apps`, copiar el repositorio y crear el archivo de configuracion:

```bash
scp -i labsuser.pem -r ms-pedidos360-* frontend-pedidos360 infra ec2-user@<ip-publica-apps>:~/
ssh -i labsuser.pem ec2-user@<ip-publica-apps>
cd ~/infra/apps
cp ../../ms-pedidos360-orders/.env.example .env   # y completar
```

El `.env` debe quedar asi:

```bash
DB_HOST=pedidos360-db.xxxxx.us-east-1.rds.amazonaws.com
DB_PORT=5432
DB_NAME=pedidos360
DB_USER=pedidos360_app
DB_PASSWORD="la-contrasena"      # entre comillas si tiene # u otros simbolos
DDL_AUTO=update

RABBITMQ_HOST=<ip-privada-de-ec2-mq-kafka>
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest
KAFKA_BOOTSTRAP_SERVERS=<ip-privada-de-ec2-mq-kafka>:9092

AZURE_ISSUER_URI=https://login.microsoftonline.com/<tenant-id>/v2.0
AZURE_AUDIENCES=api://<client-id>,<client-id>

ALLOWED_ORIGINS=http://localhost:4200
```

Las comillas en la contrasena no son opcionales si contiene `#`: Docker Compose
interpreta ese caracter como inicio de comentario y trunca el valor.

`AZURE_AUDIENCES` lleva los dos formatos porque el claim `aud` es
`api://<client-id>` en tokens v1 y el GUID pelado en tokens v2.

Construir y levantar todo:

```bash
sudo docker compose build
sudo docker compose up -d
sudo docker compose ps
```

Las imagenes se construyen en la instancia con un Dockerfile multi-etapa: Maven
compila y la imagen final solo lleva el JRE. La primera construccion tarda
varios minutos porque descarga las dependencias.

## 7. API Gateway

En **API Gateway → Create API → HTTP API**, nombre `pedidos360-api`.

### 7.1 Integraciones

Cinco, todas de tipo **HTTP URI** con metodo `ANY`, apuntando a la IP publica de
`ec2-apps`:

| Integracion | URL |
|---|---|
| orders (listado y creacion) | `http://<ip-apps>:8080/api/orders` |
| orders (detalle y estados) | `http://<ip-apps>:8080/api/orders/{proxy}` |
| catalog | `http://<ip-apps>:8081/api/catalog/{proxy}` |
| report | `http://<ip-apps>:8083/api/report/{proxy}` |
| audit | `http://<ip-apps>:8084/api/audit/{proxy}` |

Orders necesita dos porque `{proxy+}` exige al menos un segmento despues de la
ruta: sin la primera integracion, `GET /api/orders` y `POST /api/orders`
devuelven 404.

### 7.2 Rutas

Cinco rutas con metodo `ANY`, cada una asociada a su integracion:

```
/api/orders
/api/orders/{proxy+}
/api/catalog/{proxy+}
/api/report/{proxy+}
/api/audit/{proxy+}
```

### 7.3 Authorizer

En **Authorization → Create and attach an authorizer**, tipo **JWT**:

- Name: `azure-ad-jwt`
- Identity source: `$request.header.Authorization`
- Issuer: `https://login.microsoftonline.com/<tenant-id>/v2.0`
- Audience: dos entradas **separadas**, `api://<client-id>` y `<client-id>`

Hay que adjuntarlo a las cinco rutas: no se aplica solo.

### 7.4 Stage y CORS

El stage `$default` se crea con **Auto-deploy** activado, asi que cada cambio se
publica solo. Anotar el **Invoke URL**, que no cambia aunque cambien las IPs de
las instancias, y ponerlo en `API_GATEWAY_URL` de `environment.prod.ts`.

Como el frontend y el Gateway estan en origenes distintos, hay que configurar
**CORS** en el Gateway. En la seccion CORS de la API:

| Campo | Valor |
|---|---|
| Access-Control-Allow-Origin | `https://<ip-apps>:8085` |
| Access-Control-Allow-Headers | `authorization,content-type` |
| Access-Control-Allow-Methods | `GET,POST,PUT,PATCH,DELETE,OPTIONS` |

En la consola hay que presionar **Add** en cada campo antes de guardar; si solo
se escribe el valor y se guarda, queda vacio.

Ademas, `ALLOWED_ORIGINS` en el `.env` del backend tiene que incluir ese mismo
origen, porque el Gateway reenvia la cabecera `Origin` al microservicio.

Un detalle a verificar: las rutas usan el metodo `ANY`, que incluye `OPTIONS`.
Si el preflight responde 401 en vez de devolver las cabeceras CORS, significa
que la ruta con authorizer esta interceptando la peticion, que el navegador
envia sin `Authorization`. En ese caso hay que agregar rutas `OPTIONS`
explicitas, sin authorizer, para cada path.

Como alternativa, `nginx.conf` conserva un proxy de `/api/*` hacia el Invoke
URL. Apuntando `environment.prod.ts` a rutas relativas, el navegador ve todo en
el mismo origen y CORS deja de intervenir. El header `Host` y
`proxy_ssl_server_name on` de ese bloque son obligatorios: API Gateway enruta
por nombre de host y exige SNI en el handshake TLS.

### 7.5 Registrar el frontend en Azure AD

Agregar `https://<ip-apps>:8085/auth/callback` como redirect URI de tipo
Single-page application. Azure AD exige `https` salvo en localhost, por eso
Nginx sirve con un certificado autofirmado que se genera durante la
construccion de la imagen. El navegador va a pedir aceptar la advertencia la
primera vez.

## 8. Verificacion

```bash
# El frontend responde
curl -sk -o /dev/null -w "%{http_code}\n" https://<ip-apps>:8085/          # 200

# El Gateway valida y rechaza sin token
curl -sk -o /dev/null -w "%{http_code}\n" https://<ip-apps>:8085/api/orders # 401

# Con un token invalido tambien rechaza
curl -sk -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer falso" https://<ip-apps>:8085/api/orders        # 401
```

Despues, iniciar sesion en `https://<ip-apps>:8085/` y recorrer el flujo: crear
un pedido, llevarlo por todos sus estados y revisar reportes y auditoria.

Para confirmar la mensajeria, en `ec2-apps`:

```bash
cd ~/infra/apps
sudo docker compose logs ms-orders | grep -E "Publicado|Encolado"
sudo docker compose logs ms-notify | grep "Enviando email"
sudo docker compose logs ms-audit  | grep "Auditado"
sudo docker compose logs ms-report | grep "Snapshot"
```

Cada evento conserva el mismo `correlationId` a lo largo de los cuatro
servicios, lo que permite seguir una operacion de punta a punta.

## 9. Ejecucion local, sin AWS

Para desarrollar sin desplegar nada:

```bash
docker compose -f infra/mq/docker-compose.yml up -d
docker compose -f infra/kafka/docker-compose.yml up -d   # KAFKA_ADVERTISED_HOST=localhost
```

Con un PostgreSQL local y la base `pedidos360` creada, cada microservicio se
levanta con `mvn spring-boot:run` tomando su `.env`. El frontend con `npm start`
usa `environment.ts`, que apunta a cada microservicio en localhost en vez de
pasar por el API Gateway.

La documentacion de cada API queda en `http://localhost:<puerto>/swagger-ui.html`.

---

## Roles y su cobertura en el codigo

El caso define los roles `Admin`, `Operador`, `Cliente` en la seccion de
seguridad, y ademas menciona `Auditor` como actor del modulo de auditoria.
Para no dejar `/audit` inalcanzable si `Auditor` no se llega a crear en Azure
AD a tiempo, el endpoint de auditoria acepta tambien `Admin`. Se recomienda
crear el App Role `Auditor` en la App Registration de todas formas.

| Modulo | Frontend | Backend |
|---|---|---|
| Catalogo (ver) | Admin, Operador | Admin, Operador, Cliente |
| Catalogo (editar) | Admin | Admin |
| Pedidos (ver) | los 3 roles | Cliente ve solo los suyos |
| Crear pedido | Cliente, Operador | Admin, Operador, Cliente |
| Cambiar estado | Admin, Operador | Admin, Operador |
| Reportes | Admin | Admin |
| Auditoria | Admin, Auditor | Admin, Auditor |
