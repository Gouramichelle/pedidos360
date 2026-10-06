package cl.pedidos360.notify.messaging;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.pedidos360.notify.messaging.comun.ConfirmacionDeMensajes;
import cl.pedidos360.notify.messaging.comun.ErrorNoRecuperable;

/**
 * Consumer sin JWT, interno: no esta expuesto por el API Gateway ni por
 * ninguna ruta HTTP publica. Solo escucha q.cmd.email.
 *
 * El listener no confirma ni rechaza mensajes por su cuenta: entrega el
 * trabajo a ConfirmacionDeMensajes, que decide entre ACK, reintento y DLQ
 * segun el tipo de error. Asi la politica de confirmacion es la misma para
 * todas las colas y se escribe una sola vez.
 */
@Component
public class EmailNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationListener.class);
    private static final String COLA = "q.cmd.email";

    /**
     * Idempotencia: evita reenviar el correo si el mismo mensaje llega dos
     * veces, cosa que pasa de verdad cuando un reintento se reencola o cuando
     * un ACK no alcanza a salir antes de que se cierre el canal.
     *
     * El eventId se registra DESPUES de procesar, no antes: si se marcara al
     * entrar, un fallo recuperable dejaria el evento marcado como procesado y
     * el reintento lo saltaria sin hacer el trabajo, confirmandolo en falso.
     *
     * El registro es en memoria a proposito. Con mas de una instancia habria
     * que moverlo a algo compartido (Redis o una tabla dedicada).
     */
    private final Set<String> procesados = ConcurrentHashMap.newKeySet();

    private final ConfirmacionDeMensajes confirmacion;

    public EmailNotificationListener(ConfirmacionDeMensajes confirmacion) {
        this.confirmacion = confirmacion;
    }

    @RabbitListener(queues = COLA)
    public void onEmailCommand(EventEnvelope<EmailCommandPayload> envelope,
            Channel canal,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

        String eventId = envelope == null ? null : envelope.eventId();
        confirmacion.procesar(COLA, eventId, canal, deliveryTag, () -> enviarCorreo(envelope));
    }

    /**
     * El trabajo propiamente tal. Lanza ErrorNoRecuperable si el mensaje viene
     * mal formado, porque reintentarlo daria exactamente el mismo resultado.
     * Cuando se conecte un proveedor real de correo, sus fallos de red o de
     * cuota son los que deberan lanzar ErrorRecuperable.
     */
    private void enviarCorreo(EventEnvelope<EmailCommandPayload> envelope) {
        validar(envelope);

        if (procesados.contains(envelope.eventId())) {
            log.info("Evento {} ya procesado antes, se descarta (idempotencia)", envelope.eventId());
            return;
        }

        EmailCommandPayload payload = envelope.payload();
        log.info("Enviando email al cliente {} por el pedido {} -> nuevo estado {} (correlationId={})",
                payload.customerId(), payload.orderId(), payload.newStatus(), envelope.correlationId());

        // Envio real de email/push queda fuera de alcance (no hay proveedor
        // SMTP/SES configurado en el laboratorio). Este es el punto de
        // extension: la llamada al EmailSender va aqui, y sus fallos de red o
        // de cuota deben envolverse en ErrorRecuperable para que el mensaje se
        // reintente en vez de irse a la DLQ.

        procesados.add(envelope.eventId());
    }

    /** Lo que no se puede arreglar reintentando se corta aqui mismo. */
    private void validar(EventEnvelope<EmailCommandPayload> envelope) {
        if (envelope == null || envelope.eventId() == null || envelope.eventId().isBlank()) {
            throw new ErrorNoRecuperable("El mensaje llego sin eventId, no se puede procesar ni deduplicar");
        }
        EmailCommandPayload payload = envelope.payload();
        if (payload == null) {
            throw new ErrorNoRecuperable("El evento " + envelope.eventId() + " llego sin payload");
        }
        if (payload.orderId() == null) {
            throw new ErrorNoRecuperable("El evento " + envelope.eventId() + " no indica a que pedido corresponde");
        }
        if (payload.customerId() == null || payload.customerId().isBlank()) {
            throw new ErrorNoRecuperable("El evento " + envelope.eventId() + " no indica a que cliente notificar");
        }
        if (payload.newStatus() == null || payload.newStatus().isBlank()) {
            throw new ErrorNoRecuperable("El evento " + envelope.eventId() + " no indica el nuevo estado del pedido");
        }
    }
}
