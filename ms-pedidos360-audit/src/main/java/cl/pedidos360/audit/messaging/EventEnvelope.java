package cl.pedidos360.audit.messaging;

import java.time.Instant;

/**
 * Refleja el envelope publicado por ms-orders en orders.events.
 *
 * El payload esta tipado de forma concreta y NO con un generico
 * EventEnvelope<T>: al deserializar, Jackson recibe solo la clase destino que
 * declara VALUE_DEFAULT_TYPE, y por borrado de tipos un parametro generico
 * llega como Object. En la practica eso hacia que payload quedara como un
 * LinkedHashMap y el listener fallara con ClassCastException en cada mensaje.
 */
public record EventEnvelope(
        String type,
        String eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        OrderEventPayload payload) {
}
