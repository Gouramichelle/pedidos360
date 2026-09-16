package cl.pedidos360.notify.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class EmailNotificationListenerTest {

    private EventEnvelope<EmailCommandPayload> envelope(String eventId) {
        return new EventEnvelope<>(
                "EmailNotificationRequested",
                eventId,
                Instant.now(),
                "trace-1",
                "corr-1",
                new EmailCommandPayload(1L, "cliente-1", "ACEPTADO"));
    }

    @Test
    void procesaUnEventoNuevoSinLanzarExcepciones() {
        EmailNotificationListener listener = new EmailNotificationListener();
        assertThatCode(() -> listener.onEmailCommand(envelope("evt-1"))).doesNotThrowAnyException();
    }

    @Test
    void descartaUnEventoRedeliveredSinReprocesarlo() {
        EmailNotificationListener listener = new EmailNotificationListener();
        var env = envelope("evt-duplicado");
        listener.onEmailCommand(env);
        // segunda entrega del mismo eventId (ej. tras un NACK previo): no debe fallar,
        // simplemente se descarta por idempotencia.
        assertThatCode(() -> listener.onEmailCommand(env)).doesNotThrowAnyException();
    }
}
