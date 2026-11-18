package cl.pedidos360.report.messaging;

import java.time.Instant;

/**
 * Debe reflejar exactamente el payload que ms-audit publica en audit.timeline.
 *
 * Es un DTO propio y no una clase compartida a proposito: si los dos servicios
 * dependieran de la misma clase, cambiarla obligaria a desplegar ambos a la
 * vez, que es justo lo que la mensajeria viene a evitar. El contrato es el
 * formato del mensaje, no el tipo de Java.
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
