package cl.pedidos360.notify.messaging.kitchen;

/** Comando que recibe la cocina para emitir el ticket de preparacion. */
public record KitchenCommandPayload(
        Long orderId,
        String customerId,
        int cantidadItems) {
}
