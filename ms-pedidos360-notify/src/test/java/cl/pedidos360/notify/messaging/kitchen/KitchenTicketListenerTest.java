package cl.pedidos360.notify.messaging.kitchen;

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

class KitchenTicketListenerTest {

    private static final long TAG = 11L;

    private Channel canal;
    private KitchenTicketListener listener;

    @BeforeEach
    void setUp() {
        canal = mock(Channel.class);
        listener = new KitchenTicketListener(new ConfirmacionDeMensajes());
    }

    private EventEnvelope<KitchenCommandPayload> envelope(String eventId, KitchenCommandPayload payload) {
        return new EventEnvelope<>("KitchenTicketRequested", eventId, Instant.now(), "t", "c", payload);
    }

    @Test
    void confirmaUnTicketBienFormado() throws IOException {
        listener.onKitchenCommand(
                envelope("evt-1", new KitchenCommandPayload(1L, "cliente-1", 3)), canal, TAG);

        verify(canal).basicAck(TAG, false);
    }

    @Test
    void mandaALaDlqUnTicketSinItems() throws IOException {
        // Un ticket sin items no tiene nada que preparar: reintentarlo daria
        // exactamente lo mismo.
        listener.onKitchenCommand(
                envelope("evt-2", new KitchenCommandPayload(1L, "cliente-1", 0)), canal, TAG);

        verify(canal).basicNack(TAG, false, false);
    }

    @Test
    void mandaALaDlqUnTicketSinPedido() throws IOException {
        listener.onKitchenCommand(
                envelope("evt-3", new KitchenCommandPayload(null, "cliente-1", 2)), canal, TAG);

        verify(canal).basicNack(TAG, false, false);
    }

    @Test
    void confirmaLaSegundaEntregaSinVolverAEmitirElTicket() throws IOException {
        var env = envelope("evt-dup", new KitchenCommandPayload(1L, "cliente-1", 2));

        listener.onKitchenCommand(env, canal, TAG);
        listener.onKitchenCommand(env, canal, TAG);

        verify(canal, times(2)).basicAck(TAG, false);
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }
}
