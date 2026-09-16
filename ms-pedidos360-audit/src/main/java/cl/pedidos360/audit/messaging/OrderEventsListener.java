package cl.pedidos360.audit.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cl.pedidos360.audit.domain.AuditEvent;
import cl.pedidos360.audit.domain.AuditEventRepository;

/**
 * Consume orders.events, persiste el registro de auditoria (fuente de verdad
 * de /api/audit) y republica en audit.timeline para quien quiera streaming en
 * vez de polling HTTP.
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);
    private static final String SOURCE = "ms-pedidos360-orders";

    private final AuditEventRepository repository;
    private final AuditTimelinePublisher timelinePublisher;

    public OrderEventsListener(AuditEventRepository repository, AuditTimelinePublisher timelinePublisher) {
        this.repository = repository;
        this.timelinePublisher = timelinePublisher;
    }

    @KafkaListener(topics = "orders.events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onOrderEvent(EventEnvelope envelope) {
        OrderEventPayload payload = envelope.payload();

        String payloadJson = String.format(
                "{\"previousStatus\":\"%s\",\"newStatus\":\"%s\",\"total\":%s,\"eventId\":\"%s\",\"correlationId\":\"%s\"}",
                payload.previousStatus(), payload.newStatus(), payload.total(), envelope.eventId(), envelope.correlationId());

        AuditEvent event = new AuditEvent(
                envelope.type(), payload.orderId(), payload.actor(), payloadJson, payload.occurredAt());
        repository.save(event);

        timelinePublisher.publicar(payload.orderId(), new AuditTimelinePayload(
                payload.orderId(), envelope.type(), event.getActor(),
                payload.previousStatus(), payload.newStatus(), payload.occurredAt(), SOURCE));

        log.info("Auditado {} del pedido {} por {} (correlationId={})",
                envelope.type(), payload.orderId(), event.getActor(), envelope.correlationId());
    }
}
