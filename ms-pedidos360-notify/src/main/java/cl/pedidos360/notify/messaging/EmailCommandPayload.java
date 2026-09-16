package cl.pedidos360.notify.messaging;

public record EmailCommandPayload(
        Long orderId,
        String customerId,
        String newStatus) {
}
