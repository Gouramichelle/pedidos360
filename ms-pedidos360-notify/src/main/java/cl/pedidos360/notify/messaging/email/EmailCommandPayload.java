package cl.pedidos360.notify.messaging.email;

public record EmailCommandPayload(
        Long orderId,
        String customerId,
        String newStatus) {
}
