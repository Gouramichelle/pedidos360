package cl.pedidos360.notify.messaging.comun;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.Channel;

/**
 * Cada prueba fija una de las tres salidas posibles de la politica de
 * confirmacion. Lo que se verifica es la llamada exacta al canal, porque es lo
 * que determina si el mensaje se descarta, vuelve a la cola o cae en la DLQ:
 *
 *   basicAck (tag, false)          -> procesado
 *   basicNack(tag, false, true)    -> requeue, se reintenta
 *   basicNack(tag, false, false)   -> a la DLQ
 */
class ConfirmacionDeMensajesTest {

    private static final String COLA = "q.cmd.email";
    private static final long TAG = 42L;

    private Channel canal;
    private ConfirmacionDeMensajes confirmacion;

    @BeforeEach
    void setUp() {
        canal = mock(Channel.class);
        confirmacion = new ConfirmacionDeMensajes();
    }

    @Test
    void confirmaConAckCuandoElTrabajoTerminaBien() throws IOException {
        confirmacion.procesar(COLA, "evt-1", canal, TAG, () -> {
        });

        verify(canal).basicAck(TAG, false);
        verify(canal, never()).basicNack(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void mandaDirectoALaDlqCuandoElErrorNoEsRecuperable() throws IOException {
        confirmacion.procesar(COLA, "evt-2", canal, TAG, () -> {
            throw new ErrorNoRecuperable("payload sin customerId");
        });

        // requeue=false: el broker lo enruta por el x-dead-letter-exchange.
        verify(canal).basicNack(TAG, false, false);
        verify(canal, never()).basicAck(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void reencolaCuandoElErrorEsRecuperableYQuedanIntentos() throws IOException {
        confirmacion.procesar(COLA, "evt-3", canal, TAG, () -> {
            throw new ErrorRecuperable("el proveedor de correo no responde");
        });

        verify(canal).basicNack(TAG, false, true);
    }

    @Test
    void terminaEnLaDlqCuandoElErrorRecuperableAgotaLosIntentos() throws IOException {
        Runnable siempreFalla = () -> {
            throw new ErrorRecuperable("el proveedor de correo sigue sin responder");
        };

        // Mismo eventId las tres veces: es la reentrega del mismo mensaje.
        confirmacion.procesar(COLA, "evt-4", canal, TAG, siempreFalla);
        confirmacion.procesar(COLA, "evt-4", canal, TAG, siempreFalla);
        confirmacion.procesar(COLA, "evt-4", canal, TAG, siempreFalla);

        verify(canal, times(2)).basicNack(TAG, false, true);   // los dos reintentos
        verify(canal, times(1)).basicNack(TAG, false, false);  // y el descarte final
    }

    @Test
    void cuentaLosIntentosPorEventoYNoEntreEventosDistintos() throws IOException {
        Runnable falla = () -> {
            throw new ErrorRecuperable("transitorio");
        };

        confirmacion.procesar(COLA, "evt-a", canal, TAG, falla);
        confirmacion.procesar(COLA, "evt-b", canal, TAG, falla);
        confirmacion.procesar(COLA, "evt-c", canal, TAG, falla);

        // Tres eventos distintos, un intento cada uno: ninguno llega al descarte.
        verify(canal, times(3)).basicNack(TAG, false, true);
        verify(canal, never()).basicNack(TAG, false, false);
    }

    @Test
    void olvidaLaCuentaDeIntentosCuandoElEventoTerminaConfirmandose() throws IOException {
        confirmacion.procesar(COLA, "evt-5", canal, TAG, () -> {
            throw new ErrorRecuperable("transitorio");
        });
        confirmacion.procesar(COLA, "evt-5", canal, TAG, () -> {
        });
        // Si el contador no se hubiese limpiado, estos dos fallos siguientes
        // llevarian el total a 3 y terminarian en la DLQ.
        confirmacion.procesar(COLA, "evt-5", canal, TAG, () -> {
            throw new ErrorRecuperable("transitorio");
        });
        confirmacion.procesar(COLA, "evt-5", canal, TAG, () -> {
            throw new ErrorRecuperable("transitorio");
        });

        verify(canal, never()).basicNack(TAG, false, false);
    }

    @Test
    void mandaALaDlqUnErrorRecuperableSiElMensajeNoTraeEventId() throws IOException {
        // Sin identificador no hay forma de contar intentos, asi que reencolar
        // dejaria el mensaje girando sin limite.
        confirmacion.procesar(COLA, null, canal, TAG, () -> {
            throw new ErrorRecuperable("transitorio");
        });

        verify(canal).basicNack(TAG, false, false);
        verify(canal, never()).basicNack(TAG, false, true);
    }

    @Test
    void trataUnErrorInesperadoComoNoRecuperable() throws IOException {
        confirmacion.procesar(COLA, "evt-6", canal, TAG, () -> {
            throw new NullPointerException("algo que nadie previo");
        });

        // Conservador: a la DLQ, que es visible, antes que a un bucle silencioso.
        verify(canal).basicNack(TAG, false, false);
    }

    @Test
    void noPropagaSiFallaElPropioAck() throws IOException {
        doThrow(new IOException("canal cerrado")).when(canal).basicAck(TAG, false);

        // Si el ACK no sale, el broker reentrega el mensaje; tumbar el
        // consumidor no aportaria nada.
        assertThatCode(() -> confirmacion.procesar(COLA, "evt-7", canal, TAG, () -> {
        })).doesNotThrowAnyException();
    }
}
