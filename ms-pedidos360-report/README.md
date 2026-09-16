# ms-pedidos360-report

Consumer de Kafka (`orders.events`) que mantiene una proyeccion de solo
lectura (`order_snapshots`) y expone los KPIs pedidos por el caso: ventas por
hora, lead time promedio y conteo de pedidos por estado activo.

Mensajes que fallan tras 3 reintentos se publican en
`orders.events.report-service.DLT` con el error y los metadatos originales.

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

Swagger UI: `http://localhost:8083/swagger-ui.html`

- `GET /api/report/kpis` (solo Admin)
