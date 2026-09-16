package cl.pedidos360.orders.messaging;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload publicado en orders.events (Kafka) por cada cambio de estado del pedido.
 * actor es el preferred_username del token que disparo la transicion -- lo
 * necesita ms-audit para el registro "quien/que/cuando" que exige el caso.
 */
public record OrderEventPayload(
        Long orderId,
        String customerId,
        String actor,
        String previousStatus,
        String newStatus,
        BigDecimal total,
        Instant occurredAt) {
}
