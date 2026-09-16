# ms-pedidos360-notify

Consumer de RabbitMQ (`q.cmd.email`), sin base de datos y sin API publica.
No requiere JWT porque no lo consume nadie desde afuera: solo escucha la
cola interna. Idempotente por `eventId` del envelope.

## Correr localmente

```bash
cp .env.example .env   # completar valores
mvn spring-boot:run
```

Requiere RabbitMQ accesible (ver `/infra/mq`).

## Tests

```bash
mvn test
```
