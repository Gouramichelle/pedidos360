package cl.pedidos360.notify.messaging.comun;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

/**
 * Politica unica de confirmacion de mensajes para todos los consumidores.
 *
 * Los listeners corren en modo MANUAL, asi que ningun mensaje se da por
 * procesado hasta que esta clase lo confirma explicitamente. Centralizar la
 * decision aqui es lo que mantiene el comportamiento identico en todas las
 * colas: si manana se agrega un consumidor nuevo, hereda esta misma politica
 * sin volver a escribirla.
 *
 * Hay exactamente tres salidas posibles, y cada una tiene su log:
 *
 *   1. El trabajo termina bien            -> ACK. El broker descarta el mensaje.
 *   2. ErrorRecuperable, quedan intentos  -> NACK con requeue. Vuelve a la cola.
 *   3. ErrorRecuperable sin intentos, o
 *      ErrorNoRecuperable, o cualquier
 *      excepcion inesperada               -> NACK sin requeue. Va a la DLQ.
 *
 * Una excepcion que no sea ninguna de las dos nuestras se trata como no
 * recuperable a proposito. Es la opcion conservadora: si no sabemos por que
 * fallo, reintentar a ciegas puede dejar el mensaje girando en la cola y
 * bloqueando a los que vienen detras. Prefiere la DLQ, que es visible y
 * revisable, antes que un bucle silencioso.
 */
@Component
public class ConfirmacionDeMensajes {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacionDeMensajes.class);

    /**
     * Intentos antes de mandar un fallo recuperable a la DLQ. AMQP no lleva la
     * cuenta de reentregas (solo marca el flag redelivered, sin numero), asi
     * que la lleva este componente en memoria, indexada por eventId.
     *
     * Para EP3 alcanza: hay un unico consumidor por cola y el proceso no se
     * reinicia durante una demo. Con mas de una instancia habria que mover
     * este contador a algo compartido, o pasar a un esquema de colas de
     * reintento con TTL, donde la cuenta la lleva el propio broker.
     */
    private static final int MAX_INTENTOS = 3;

    private final Map<String, Integer> intentosPorEvento = new ConcurrentHashMap<>();

    /**
     * Ejecuta el trabajo del consumidor y confirma el mensaje segun el
     * resultado. El listener solo aporta la logica de negocio; toda la
     * conversacion con el broker ocurre aqui.
     */
    public void procesar(String cola, String eventId, Channel canal, long deliveryTag, Runnable trabajo) {
        try {
            trabajo.run();
            confirmar(cola, eventId, canal, deliveryTag);

        } catch (ErrorNoRecuperable ex) {
            log.error("[{}] Evento {} no es recuperable, va directo a la DLQ: {}",
                    cola, eventId, ex.getMessage());
            descartar(cola, eventId, canal, deliveryTag);

        } catch (ErrorRecuperable ex) {
            if (eventId == null) {
                // Sin identificador no se pueden contar los intentos, asi que
                // reencolar dejaria el mensaje girando sin limite. Un mensaje
                // sin eventId ya es defectuoso de por si: a la DLQ.
                log.error("[{}] Mensaje sin eventId fallo de forma recuperable, "
                        + "pero sin identificador no se puede limitar el reintento: va a la DLQ", cola);
                descartar(cola, null, canal, deliveryTag);
                return;
            }
            int intento = intentosPorEvento.merge(eventId, 1, Integer::sum);
            if (intento < MAX_INTENTOS) {
                log.warn("[{}] Evento {} fallo de forma recuperable (intento {} de {}), se reencola: {}",
                        cola, eventId, intento, MAX_INTENTOS, ex.getMessage());
                reencolar(cola, eventId, canal, deliveryTag);
            } else {
                log.error("[{}] Evento {} agoto los {} intentos, va a la DLQ: {}",
                        cola, eventId, MAX_INTENTOS, ex.getMessage());
                descartar(cola, eventId, canal, deliveryTag);
            }

        } catch (RuntimeException ex) {
            log.error("[{}] Evento {} fallo con un error inesperado, se trata como no recuperable y va a la DLQ",
                    cola, eventId, ex);
            descartar(cola, eventId, canal, deliveryTag);
        }
    }

    /** ACK: el mensaje se proceso y el broker puede olvidarlo. */
    private void confirmar(String cola, String eventId, Channel canal, long deliveryTag) {
        try {
            canal.basicAck(deliveryTag, false);
            olvidar(eventId);
            log.info("[{}] Evento {} confirmado (ACK)", cola, eventId);
        } catch (IOException ex) {
            // Si el ACK no sale, el broker no se entero de nada: al cerrarse el
            // canal el mensaje vuelve a la cola y se reprocesa. La idempotencia
            // del consumidor es la que evita el efecto duplicado.
            log.error("[{}] No se pudo confirmar el evento {}, el broker lo reentregara: {}",
                    cola, eventId, ex.getMessage());
        }
    }

    /** El eventId puede venir nulo si el mensaje llego mal formado. */
    private void olvidar(String eventId) {
        if (eventId != null) {
            intentosPorEvento.remove(eventId);
        }
    }

    /** NACK con requeue: vuelve a la cola para intentarlo de nuevo. */
    private void reencolar(String cola, String eventId, Channel canal, long deliveryTag) {
        try {
            canal.basicNack(deliveryTag, false, true);
        } catch (IOException ex) {
            log.error("[{}] No se pudo reencolar el evento {}: {}", cola, eventId, ex.getMessage());
        }
    }

    /**
     * NACK sin requeue: el broker lo enruta a la DLQ a traves del
     * x-dead-letter-exchange con el que se declaro la cola.
     */
    private void descartar(String cola, String eventId, Channel canal, long deliveryTag) {
        try {
            canal.basicNack(deliveryTag, false, false);
            olvidar(eventId);
        } catch (IOException ex) {
            log.error("[{}] No se pudo descartar el evento {} hacia la DLQ: {}",
                    cola, eventId, ex.getMessage());
        }
    }
}
