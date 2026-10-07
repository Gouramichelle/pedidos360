package cl.pedidos360.notify.messaging.invoice;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.Channel;

import cl.pedidos360.notify.messaging.comun.ConfirmacionDeMensajes;
import cl.pedidos360.notify.messaging.comun.EventEnvelope;

class InvoiceGenerationListenerTest {

    private static final long TAG = 13L;

    private Channel canal;
    private InvoiceGenerationListener listener;

    @BeforeEach
    void setUp() {
        canal = mock(Channel.class);
        listener = new InvoiceGenerationListener(new ConfirmacionDeMensajes());
    }

    private EventEnvelope<InvoiceCommandPayload> envelope(String eventId, InvoiceCommandPayload payload) {
        return new EventEnvelope<>("InvoiceGenerationRequested", eventId, Instant.now(), "t", "c", payload);
    }

    @Test
    void confirmaUnDocumentoBienFormado() throws IOException {
        listener.onInvoiceCommand(
                envelope("evt-1", new InvoiceCommandPayload(1L, "cliente-1", new BigDecimal("19990"))),
                canal, TAG);

        verify(canal).basicAck(TAG, false);
    }

    @Test
    void aceptaUnTotalCeroPorqueUnPedidoPuedeQuedarSinCosto() throws IOException {
        listener.onInvoiceCommand(
                envelope("evt-2", new InvoiceCommandPayload(1L, "cliente-1", BigDecimal.ZERO)), canal, TAG);

        verify(canal).basicAck(TAG, false);
    }

    @Test
    void mandaALaDlqUnDocumentoConTotalNegativo() throws IOException {
        listener.onInvoiceCommand(
                envelope("evt-3", new InvoiceCommandPayload(1L, "cliente-1", new BigDecimal("-100"))),
                canal, TAG);

        verify(canal).basicNack(TAG, false, false);
    }

    @Test
    void mandaALaDlqUnDocumentoSinTotal() throws IOException {
        listener.onInvoiceCommand(
                envelope("evt-4", new InvoiceCommandPayload(1L, "cliente-1", null)), canal, TAG);

        verify(canal).basicNack(TAG, false, false);
    }

    @Test
    void confirmaLaSegundaEntregaSinVolverAEmitirElDocumento() throws IOException {
        var env = envelope("evt-dup", new InvoiceCommandPayload(1L, "cliente-1", new BigDecimal("500")));

        listener.onInvoiceCommand(env, canal, TAG);
        listener.onInvoiceCommand(env, canal, TAG);

        verify(canal, times(2)).basicAck(TAG, false);
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }
}
