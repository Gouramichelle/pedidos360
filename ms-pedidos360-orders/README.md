# ms-pedidos360-orders

CRUD de pedidos, maquina de estados (`CREADO -> ACEPTADO -> EN_PREPARACION ->
DESPACHADO -> ENTREGADO` / `CANCELADO`), productor de `orders.events` (Kafka)
y del comando de notificacion `email.send` (RabbitMQ).

Ver el README raiz del workspace para la justificacion de por que no existe
un BFF y como se reparte esa responsabilidad entre API Gateway y Spring
Security.

## Correr localmente

```bash
cp .env.example .env   # completar valores
mvn spring-boot/run
```

Requiere PostgreSQL, RabbitMQ y Kafka accesibles (ver `/infra` en la raiz del
workspace para levantarlos con Docker Compose).

## Tests

```bash
mvn test
```

Cubre la maquina de estados (`OrderStatusTest`, `OrderTest`), la logica de
negocio (`OrderServiceTest`, con mocks del cliente a catalogo y los
publicadores) y la validacion de JWT (`AudienceValidatorTest`,
`SecurityConfigTest`).

## API

Swagger UI: `http://localhost:8080/swagger-ui.html`

- `GET /api/orders` — lista (todos para Admin/Operador, propios para Cliente)
- `GET /api/orders/{id}`
- `POST /api/orders`
- `PATCH /api/orders/{id}/status`
