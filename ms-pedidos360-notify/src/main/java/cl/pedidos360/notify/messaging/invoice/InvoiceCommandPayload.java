package cl.pedidos360.notify.messaging.invoice;

import java.math.BigDecimal;

/** Comando para generar el documento de cobro de un pedido entregado. */
public record InvoiceCommandPayload(
        Long orderId,
        String customerId,
        BigDecimal total) {
}
