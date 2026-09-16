package cl.pedidos360.audit.messaging;

import java.time.Instant;

/**
 * Payload publicado en audit.timeline: registro "quien/que/cuando/desde donde"
 * exigido por el caso, derivado de cada evento de orders.events.
 */
public record AuditTimelinePayload(
        Long orderId,
        String eventType,
        String actor,
        String previousStatus,
        String newStatus,
        Instant occurredAt,
        String source) {
}
