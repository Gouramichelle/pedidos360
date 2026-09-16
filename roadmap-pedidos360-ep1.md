# Roadmap de Desarrollo — Pedidos360 (EP1, DSY1107)

## 0. Decisiones tomadas

| Decisión | Valor |
|---|---|
| Alcance EP1 | 5 microservicios completos: orders, catalog, notify, report, audit |
| Mensajería | RabbitMQ (comandos) + Kafka/Zookeeper (eventos) desde esta entrega |
| BFF | **Se elimina** — validación JWT se hace en API Gateway + en cada microservicio |
| Base de datos | AWS RDS **PostgreSQL** (reemplaza Oracle del caso original) |
| Infraestructura | Desde cero: cuenta AWS, tenant Azure AD, VPC, EC2, RDS, API Gateway |
| Equipo | Pareja (según pauta) |
| Plazo | 2 semanas (Taite 7 + trabajo personal) |

> **Nota de riesgo:** el indicador de 40% de la pauta nombra explícitamente "BFF". Al no tenerlo, ese 40% se cubre reinterpretando "BFF" como "capa de validación de borde" = API Gateway (JWT Authorizer) + Spring Security en cada microservicio. Documentar esto en el README del repo backend y, si es posible, confirmarlo con el/la docente antes del plazo de entrega.

---

## 1. Arquitectura ajustada

```
[Angular + MSAL] --Bearer JWT--> [AWS API Gateway HTTP API + JWT Authorizer]
                                        |  (issuer/audience = Azure AD)
        +-------------------------------+-------------------------------+
        |            |             |              |                    |
   ms-orders     ms-catalog    ms-notify      ms-report            ms-audit
   (Spring Sec.  (Spring Sec.  (consumer      (consumer Kafka,     (consumer Kafka,
    valida JWT    valida JWT    RabbitMQ,      Spring Sec.          Spring Sec.
    de nuevo,     de nuevo)     sin JWT,        read-only)           read-only)
    roles)                      interno)
        |              |                              ^                 ^
        v              v                              |                 |
   RDS PostgreSQL  RDS PostgreSQL                 Kafka Cluster <---- orders.events
   (orders)        (catalog)                      (ec2-kafka)         (publica ms-orders)
        |
        v
   RabbitMQ Cluster (ec2-mq) --consume--> ms-notify
```

Azure AD sigue siendo el IDaaS (login MSAL) y el API Gateway sigue validando el JWT antes de enrutar — igual que en el caso original, solo que sin el salto intermedio por un BFF.

---

## 2. Estructura de repositorios GitHub

```
/frontend-pedidos360        (Angular 18+, MSAL Angular, guards por rol)
/ms-pedidos360-orders       (Spring Boot 3+, Java 21, RDS Postgres, produce orders.events a Kafka, produce a RabbitMQ)
/ms-pedidos360-catalog      (Spring Boot 3+, Java 21, RDS Postgres)
/ms-pedidos360-notify       (Spring Boot 3+, sin DB, consumer RabbitMQ: q.cmd.email)
/ms-pedidos360-report       (Spring Boot 3+, RDS Postgres, consumer Kafka: orders.events)
/ms-pedidos360-audit        (Spring Boot 3+, RDS Postgres, consumer Kafka: orders.events -> audit.timeline)
/infra
  /apps/docker-compose.yml       (los 5 microservicios)
  /mq/docker-compose.yml         (RabbitMQ + management UI)
  /kafka/docker-compose.yml      (Zookeeper + 1 broker Kafka)
  /docs/                         (diagramas, decisiones de arquitectura, .env.example)
```

Cada repo con `.gitignore` correspondiente a su stack (Node/Angular para el front; Maven/Gradle + `application-local.yml` para el back) — **nada de secretos ni `target/`/`node_modules/` subido**.

---

## 3. Modelo de datos (PostgreSQL en RDS) — nivel alto

| Servicio | Tablas principales |
|---|---|
| orders | `orders(id, customer_id, status, created_at, accepted_at, dispatched_at, delivered_at)`, `order_items(id, order_id, product_id, qty, price)` |
| catalog | `products(id, sku, name, price, stock)` |
| report | tablas de agregación/materialized views alimentadas por el consumer Kafka |
| audit | `audit_events(id, event_type, entity_id, actor, payload_json, occurred_at)` |
| notify | sin DB (stateless, solo consumer) |

Recomendación: una única instancia RDS PostgreSQL (Free Tier `db.t3.micro`) con **una base de datos por esquema** (`orders`, `catalog`, `report`, `audit`) en vez de 4 instancias separadas — reduce costo y tiempo de aprovisionamiento para el MVP.

---

## 4. Topología de mensajería (heredada del caso, sin cambios)

**RabbitMQ** — exchanges `cmd.direct`, `cmd.topic`, `cmd.dead.dlx`; colas `q.cmd.email` (+ `.dlq`), `q.cmd.kitchen` (+ `.dlq`), `q.cmd.invoice` (+ `.dlq`). Para EP1 basta con dejar operativa `q.cmd.email` (notificación de cambio de estado); el resto se declara pero puede quedar sin publicadores activos aún.

**Kafka** — tópicos `orders.events` (3 particiones, retención 3–7 días) y `audit.timeline` (3 particiones, compact+delete, 14–30 días). `ms-orders` publica `OrderCreated/Accepted/Preparing/Dispatched/Delivered/Cancelled`; `ms-audit` y `ms-report` consumen.

---

## 5. Plan de infraestructura (orden de aprovisionamiento)

1. **Azure AD (Entra External ID):** crear tenant, App Registration "Pedidos360" (clientId, redirectUri `http://localhost:4200` y luego dominio real), exponer API (`api://<API_CLIENT_ID>`) con scopes, definir roles de app (Admin/Operador/Cliente) y asignarlos a usuarios de prueba.
2. **AWS — cuenta y red:** crear cuenta, VPC con subnets públicas/privadas, Security Groups (abrir solo 5672 AMQP, 9092 Kafka, y los puertos HTTP internos de cada microservicio).
3. **RDS PostgreSQL:** instancia `db.t3.micro`, subnet privada, security group que solo permite acceso desde las instancias EC2 de la app.
4. **EC2:** una instancia para apps (los 5 microservicios vía Docker Compose), una para RabbitMQ, una para Kafka+Zookeeper (o todo junto en una sola instancia si el budget/tiempo aprieta — válido para MVP).
5. **API Gateway (HTTP API):** crear rutas `/api/orders/*`, `/api/catalog/*`, `/api/report/*`, `/api/audit/*`; configurar JWT Authorizer con `issuer = https://login.microsoftonline.com/<TENANT_ID>/v2.0` y `audience = api://<API_CLIENT_ID>`; integraciones apuntando a las IPs/DNS de las instancias EC2.

---

## 6. Cronograma — 2 semanas (pareja)

**Semana 1 — Infra + dominio core**

| Día | Persona A (backend/infra) | Persona B (frontend) |
|---|---|---|
| 1–2 (Taite 7) | Azure AD tenant + App Registration + cuenta AWS + VPC | Scaffolding Angular 18, routing, layout base |
| 3 | RDS Postgres + esquemas + EC2 apps | Integración MSAL (login, guard, interceptor) |
| 4–5 | `ms-catalog` CRUD completo (entidades, repos, controller) | Vista `/catalog`, `/login`, `/dashboard` conectadas a mocks |
| 6–7 | `ms-orders` CRUD + máquina de estados (no despachar sin aceptar) | Vista `/orders` (list/detail/status badge) |

**Semana 2 — Mensajería, seguridad de borde, integración y entrega**

| Día | Persona A | Persona B |
|---|---|---|
| 8 | Docker Compose RabbitMQ + Kafka+ZK en EC2 | Conectar `/orders` y `/catalog` al API Gateway real |
| 9 | `ms-orders` publica a Kafka (`orders.events`) y a RabbitMQ (`q.cmd.email`) | Manejo de roles en UI (ocultar acciones por rol) |
| 10 | `ms-notify` (consumer RabbitMQ) + `ms-audit`/`ms-report` (consumers Kafka) | Vista `/audit` y `/reports` (aunque sea básica) |
| 11 | API Gateway: JWT Authorizer + rutas todas las integraciones | Interceptor renovando token, manejo de expiración |
| 12 | Spring Security en cada servicio (issuer-uri + validación de rol por endpoint) | Pruebas end-to-end manuales contra el Gateway desplegado |
| 13 | Pruebas básicas (unit/integration), limpieza de `.gitignore`, README con la justificación de "sin BFF" | Pulido UI, README frontend |
| 14 | Deploy final en EC2, smoke test completo, preparar presentación | Preparar presentación |

---

## 7. Checklist de cumplimiento vs. pauta EP1

- [ ] MSAL integrado: login/logout funcionan, guards por rol, interceptor adjunta token en cada request.
- [ ] Token obtenido permite consumir el API Gateway sin errores 401/403 inesperados.
- [ ] API Gateway valida issuer + audience antes de enrutar (equivalente al "API Manager" del diagrama).
- [ ] Cada microservicio valida el JWT (firma, expiración, audience) vía Spring Security y aplica autorización por rol, respondiendo códigos de error adecuados (401/403).
- [ ] README documenta por qué no hay BFF y cómo se cubre la validación de borde.
- [ ] Backend compila, sigue buenas prácticas, tiene pruebas básicas.
- [ ] Frontend compila sin errores, vistas funcionales para los 3 roles.
- [ ] Conexión a RDS PostgreSQL vía entidades/repositorios/propiedades, sin credenciales hardcodeadas (usar variables de entorno).
- [ ] `.gitignore` correcto en ambos contextos (nada de `target/`, `node_modules/`, `.env`, secretos).
- [ ] Enlaces a los 6 repos (front + 5 microservicios) copiados a AVA y enviados al correo del docente dentro del plazo.

---

## 8. Entregables finales

1. 6 repositorios GitHub (frontend + 5 microservicios) con código funcional.
2. `docker-compose.yml` para apps, mq y kafka en `/infra`.
3. Documentación de decisiones de arquitectura (incl. justificación de eliminar el BFF).
4. Presentación con demo en vivo: login MSAL → creación de pedido → cambio de estado → notificación → evento visible en auditoría/reportería.
