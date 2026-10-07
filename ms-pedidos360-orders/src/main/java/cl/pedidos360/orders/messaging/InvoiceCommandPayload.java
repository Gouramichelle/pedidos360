package cl.pedidos360.orders.messaging;

import java.math.BigDecimal;

/**
 * Payload publicado en q.cmd.invoice para generar el documento de cobro. Se
 * dispara al entregar el pedido: antes de eso el monto todavia puede cambiar
 * si el pedido se cancela.
 */
public record InvoiceCommandPayload(
        Long orderId,
        String customerId,
        BigDecimal total) {
}
