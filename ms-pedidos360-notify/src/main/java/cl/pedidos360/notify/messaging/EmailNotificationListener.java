package cl.pedidos360.notify.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumer sin JWT, interno: no esta expuesto por el API Gateway ni por
 * ninguna ruta HTTP publica. Solo escucha q.cmd.email.
 *
 * Idempotencia: se usa eventId para no reprocesar un mensaje redelivered tras
 * un NACK previo (ej. si el broker reintento la entrega). El registro es en
 * memoria a proposito -- para EP1 basta un unico consumer y no hay reinicios
 * frecuentes del proceso durante la demo; si esto escalara a mas de una
 * instancia habria que mover el set de eventId procesados a algo compartido
 * (Redis, o una tabla dedicada).
 */
@Component
public class EmailNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationListener.class);

    private final java.util.Set<String> procesados = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @RabbitListener(queues = "q.cmd.email")
    public void onEmailCommand(EventEnvelope<EmailCommandPayload> envelope) {
        if (!procesados.add(envelope.eventId())) {
            log.info("Evento {} ya procesado antes, se descarta (idempotencia)", envelope.eventId());
            return;
        }

        EmailCommandPayload payload = envelope.payload();
        log.info("Enviando email al cliente {} por el pedido {} -> nuevo estado {} (correlationId={})",
                payload.customerId(), payload.orderId(), payload.newStatus(), envelope.correlationId());

        // Envio real de email/push queda fuera de alcance de EP1 (no hay
        // proveedor SMTP/SES configurado en el Learner Lab). Se deja el punto
        // de extension aqui: reemplazar este log por una llamada a un
        // EmailSender cuando se conecte SES o un SMTP real.
    }
}
