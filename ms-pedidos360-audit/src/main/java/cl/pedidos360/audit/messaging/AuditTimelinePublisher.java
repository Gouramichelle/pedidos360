package cl.pedidos360.audit.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Republica cada evento de orders.events en audit.timeline, ya con forma de
 * registro "quien/que/cuando/desde donde". Separar el topico de auditoria del
 * topico de negocio permite que audit.timeline tenga su propia politica de
 * retencion (compact+delete, 14-30 dias) sin heredar la de orders.events.
 */
@Component
public class AuditTimelinePublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditTimelinePublisher.class);
    private static final String TOPIC = "audit.timeline";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AuditTimelinePublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publicar(Long orderId, AuditTimelinePayload payload) {
        Message<Object> message = MessageBuilder
                .withPayload((Object) payload)
                .setHeader(KafkaHeaders.TOPIC, TOPIC)
                .setHeader(KafkaHeaders.KEY, String.valueOf(orderId))
                .build();
        kafkaTemplate.send(message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("No se pudo publicar en {} para el pedido {}: {}", TOPIC, orderId, ex.getMessage(), ex);
            }
        });
    }
}
