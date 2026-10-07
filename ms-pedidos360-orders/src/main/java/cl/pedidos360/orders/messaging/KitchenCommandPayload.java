package cl.pedidos360.orders.messaging;

/**
 * Payload publicado en q.cmd.kitchen para que la cocina emita el ticket de
 * preparacion. Se dispara al aceptar el pedido, que es cuando la cocina
 * necesita saber que hay trabajo.
 */
public record KitchenCommandPayload(
        Long orderId,
        String customerId,
        int cantidadItems) {
}
