# ms-pedidos360-audit

Consumer de Kafka (`orders.events`) que persiste el registro de auditoria
`audit_events(id, event_type, entity_id, actor, payload_json, occurred_at)`
y republica cada evento en `audit.timeline` para consumidores de streaming.

Solo lectura via `/api/audit/*`. Acepta los roles `Auditor` y `Admin` (ver
justificacion en el README raiz del workspace).

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

Swagger UI: `http://localhost:8084/swagger-ui.html`

- `GET /api/audit/events?entityId=&actor=&from=&to=`
