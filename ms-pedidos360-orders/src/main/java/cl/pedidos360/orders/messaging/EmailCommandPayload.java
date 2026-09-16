package cl.pedidos360.orders.messaging;

/** Payload publicado en q.cmd.email (RabbitMQ) para que ms-notify avise al cliente. */
public record EmailCommandPayload(
        Long orderId,
        String customerId,
        String newStatus) {
}
