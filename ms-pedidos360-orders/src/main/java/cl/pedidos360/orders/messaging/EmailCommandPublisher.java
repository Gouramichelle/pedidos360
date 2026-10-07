package cl.pedidos360.orders.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import cl.pedidos360.orders.config.MensajeriaProperties;
import cl.pedidos360.orders.domain.Order;

/**
 * Comando de notificacion al cliente, en cada cambio de estado del pedido.
 *
 * Va por el exchange TOPIC: la routing key se compone por tema, asi que
 * manana se puede publicar "email.send.urgente" y la cola lo sigue recibiendo
 * por el patron "email.*" sin tocar ningun binding.
 */
@Component
public class EmailCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(EmailCommandPublisher.class);
    private static final String FLUJO = "email";

    private final RabbitTemplate rabbitTemplate;
    private final MensajeriaProperties mensajeria;

    public EmailCommandPublisher(RabbitTemplate rabbitTemplate, MensajeriaProperties mensajeria) {
        this.rabbitTemplate = rabbitTemplate;
        this.mensajeria = mensajeria;
    }

    public void notificarCambioEstado(Order order, String traceId, String correlationId) {
        MensajeriaProperties.Flujo flujo = mensajeria.flujo(FLUJO);
        EmailCommandPayload payload = new EmailCommandPayload(
                order.getId(), order.getCustomerId(), order.getStatus().name());
        EventEnvelope<EmailCommandPayload> envelope = EventEnvelope.of(
                "EmailNotificationRequested", traceId, correlationId, payload);
        try {
            rabbitTemplate.convertAndSend(mensajeria.exchanges().topic(), flujo.routingKey(), envelope);
            log.info("Encolado email para el pedido {} ({}), correlationId={}",
                    order.getId(), order.getStatus(), correlationId);
        } catch (Exception ex) {
            // No se relanza: la notificacion es best-effort y nunca debe tumbar
            // la transaccion que cambia el estado del pedido.
            log.error("No se pudo encolar el email del pedido {}: {}", order.getId(), ex.getMessage(), ex);
        }
    }
}
