package cl.pedidos360.notify.messaging.email;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.Channel;

import cl.pedidos360.notify.messaging.comun.ConfirmacionDeMensajes;
import cl.pedidos360.notify.messaging.comun.EventEnvelope;

/**
 * El listener se prueba con la politica de confirmacion real y el canal
 * mockeado, porque lo que importa no es que el metodo retorne sino con que
 * llamada al broker termina cada caso.
 */
class EmailNotificationListenerTest {

    private static final long TAG = 7L;

    private Channel canal;
    private EmailNotificationListener listener;

    @BeforeEach
    void setUp() {
        canal = mock(Channel.class);
        listener = new EmailNotificationListener(new ConfirmacionDeMensajes());
    }

    private EventEnvelope<EmailCommandPayload> envelope(String eventId, EmailCommandPayload payload) {
        return new EventEnvelope<>("EmailNotificationRequested", eventId, Instant.now(),
                "trace-1", "corr-1", payload);
    }

    private EmailCommandPayload payloadValido() {
        return new EmailCommandPayload(1L, "cliente-1", "ACEPTADO");
    }

    @Test
    void confirmaUnEventoBienFormado() throws IOException {
        listener.onEmailCommand(envelope("evt-1", payloadValido()), canal, TAG);

        verify(canal).basicAck(TAG, false);
    }

    @Test
    void confirmaTambienLaSegundaEntregaDelMismoEventoSinReprocesarlo() throws IOException {
        var env = envelope("evt-duplicado", payloadValido());

        listener.onEmailCommand(env, canal, TAG);
        listener.onEmailCommand(env, canal, TAG);

        // Las dos entregas se confirman: la segunda se descarta por
        // idempotencia, pero igual hay que sacarla de la cola.
        verify(canal, times(2)).basicAck(TAG, false);
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void mandaALaDlqUnEventoSinPayload() throws IOException {
        listener.onEmailCommand(envelope("evt-2", null), canal, TAG);

        verify(canal).basicNack(TAG, false, false);
    }

    @Test
    void mandaALaDlqUnEventoSinCliente() throws IOException {
        listener.onEmailCommand(
                envelope("evt-3", new EmailCommandPayload(1L, "  ", "ACEPTADO")), canal, TAG);

        verify(canal).basicNack(TAG, false, false);
    }

    @Test
    void mandaALaDlqUnEventoSinPedido() throws IOException {
        listener.onEmailCommand(
                envelope("evt-4", new EmailCommandPayload(null, "cliente-1", "ACEPTADO")), canal, TAG);

        verify(canal).basicNack(TAG, false, false);
    }

    @Test
    void mandaALaDlqUnEventoSinEstado() throws IOException {
        listener.onEmailCommand(
                envelope("evt-5", new EmailCommandPayload(1L, "cliente-1", null)), canal, TAG);

        verify(canal).basicNack(TAG, false, false);
    }

    @Test
    void mandaALaDlqUnMensajeSinEventIdPorqueNoSePuedeDeduplicar() throws IOException {
        listener.onEmailCommand(envelope(null, payloadValido()), canal, TAG);

        verify(canal).basicNack(TAG, false, false);
    }

    @Test
    void unEventoRechazadoNoQuedaMarcadoComoProcesado() throws IOException {
        // Primero llega mal formado y va a la DLQ.
        listener.onEmailCommand(envelope("evt-6", null), canal, TAG);
        // Si el productor lo reenvia corregido con el mismo eventId, tiene que
        // procesarse: marcarlo como visto en el primer intento lo habria
        // confirmado en falso sin enviar nada.
        listener.onEmailCommand(envelope("evt-6", payloadValido()), canal, TAG);

        verify(canal).basicNack(TAG, false, false);
        verify(canal).basicAck(TAG, false);
    }
}
