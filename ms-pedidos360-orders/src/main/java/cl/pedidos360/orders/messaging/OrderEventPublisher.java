package cl.pedidos360.orders.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import cl.pedidos360.orders.domain.Order;
import cl.pedidos360.orders.domain.OrderStatus;

/**
 * Publica en orders.events cada transicion de estado de un pedido.
 * Es la fuente de verdad que consumen ms-report y ms-audit.
 * Se usa el id del pedido como key: garantiza que todos los eventos de un
 * mismo pedido caigan en la misma particion y se procesen en orden.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);
    private static final String TOPIC = "orders.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publicarCambioEstado(Order order, OrderStatus estadoAnterior, String actor, String traceId, String correlationId) {
        OrderEventPayload payload = new OrderEventPayload(
                order.getId(),
                order.getCustomerId(),
                actor,
                estadoAnterior.name(),
                order.getStatus().name(),
                order.total(),
                java.time.Instant.now());

        EventEnvelope<OrderEventPayload> envelope = EventEnvelope.of(
                order.getStatus().eventType(), traceId, correlationId, payload);

        Message<Object> message = MessageBuilder
                .withPayload((Object) envelope)
                .setHeader(KafkaHeaders.TOPIC, TOPIC)
                .setHeader(KafkaHeaders.KEY, String.valueOf(order.getId()))
                .build();

        kafkaTemplate.send(message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("No se pudo publicar {} para el pedido {} en {}: {}",
                        envelope.type(), order.getId(), TOPIC, ex.getMessage(), ex);
            } else {
                log.info("Publicado {} para el pedido {} en {} offset {}",
                        envelope.type(), order.getId(), TOPIC,
                        result.getRecordMetadata().offset());
            }
        });
    }
}
