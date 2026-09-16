package cl.pedidos360.orders.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import cl.pedidos360.orders.domain.Order;
import cl.pedidos360.orders.config.RabbitTopologyConfig;

/**
 * Publica el comando de notificacion por email cada vez que cambia el estado
 * de un pedido. Va al exchange topic con routing key "email.send" para poder
 * variarla despues (ej. "email.send.high") sin tocar el binding.
 */
@Component
public class EmailCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(EmailCommandPublisher.class);
    private static final String ROUTING_KEY = "email.send";

    private final RabbitTemplate rabbitTemplate;

    public EmailCommandPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void notificarCambioEstado(Order order, String traceId, String correlationId) {
        EmailCommandPayload payload = new EmailCommandPayload(
                order.getId(), order.getCustomerId(), order.getStatus().name());
        EventEnvelope<EmailCommandPayload> envelope = EventEnvelope.of(
                "EmailNotificationRequested", traceId, correlationId, payload);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitTopologyConfig.EXCHANGE_TOPIC, ROUTING_KEY, envelope);
            log.info("Encolado email para el pedido {} ({}), correlationId={}",
                    order.getId(), order.getStatus(), correlationId);
        } catch (Exception ex) {
            // No se relanza: la notificacion es best-effort y nunca debe tumbar
            // la transaccion que cambia el estado del pedido.
            log.error("No se pudo encolar el email del pedido {}: {}", order.getId(), ex.getMessage(), ex);
        }
    }
}
