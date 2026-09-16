package cl.pedidos360.notify.messaging;

import java.time.Instant;

/** Debe reflejar exactamente el envelope publicado por ms-orders. */
public record EventEnvelope<T>(
        String type,
        String eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        T payload) {
}
