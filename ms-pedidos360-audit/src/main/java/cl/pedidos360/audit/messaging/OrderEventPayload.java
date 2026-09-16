package cl.pedidos360.audit.messaging;

import java.math.BigDecimal;
import java.time.Instant;

/** Debe reflejar exactamente el payload publicado por ms-orders en orders.events. */
public record OrderEventPayload(
        Long orderId,
        String customerId,
        String actor,
        String previousStatus,
        String newStatus,
        BigDecimal total,
        Instant occurredAt) {
}
