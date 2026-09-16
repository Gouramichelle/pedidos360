# ms-pedidos360-catalog

CRUD de productos y stock. El stock se descuenta via
`PATCH /api/catalog/products/{id}/stock/decrease`, invocado por ms-orders al
aceptar un pedido.

## Correr localmente

```bash
cp .env.example .env   # completar valores
mvn spring-boot:run
```

## Tests

```bash
mvn test
```

## API

Swagger UI: `http://localhost:8081/swagger-ui.html`
