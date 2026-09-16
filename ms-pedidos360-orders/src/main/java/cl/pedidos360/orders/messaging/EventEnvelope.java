package cl.pedidos360.orders.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope comun para todo mensaje publicado, tanto a RabbitMQ como a Kafka.
 * Exigido por el caso: type, eventId, timestamp, traceId, correlationId.
 *
 * correlationId agrupa todos los mensajes que nacen de una misma accion de
 * usuario (por ejemplo, aceptar un pedido dispara un evento a Kafka y un
 * comando a RabbitMQ con el mismo correlationId). traceId identifica la
 * peticion HTTP puntual que origino el mensaje.
 */
public record EventEnvelope<T>(
        String type,
        String eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        T payload) {

    public static <T> EventEnvelope<T> of(String type, String traceId, String correlationId, T payload) {
        return new EventEnvelope<>(type, UUID.randomUUID().toString(), Instant.now(), traceId, correlationId, payload);
    }
}
