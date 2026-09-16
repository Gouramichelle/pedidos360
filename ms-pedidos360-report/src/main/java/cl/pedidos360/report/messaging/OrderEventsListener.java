package cl.pedidos360.report.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cl.pedidos360.report.domain.OrderSnapshot;
import cl.pedidos360.report.domain.OrderSnapshotRepository;

/**
 * Consume orders.events y mantiene la proyeccion order_snapshots al dia.
 * Upsert por orderId: como Kafka garantiza orden dentro de una particion y
 * ms-orders usa el orderId como key, todos los eventos de un mismo pedido
 * llegan en orden a este consumer.
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final OrderSnapshotRepository repository;

    public OrderEventsListener(OrderSnapshotRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "orders.events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onOrderEvent(EventEnvelope envelope) {
        OrderEventPayload payload = envelope.payload();
        OrderSnapshot snapshot = repository.findById(payload.orderId())
                .orElseGet(() -> new OrderSnapshot(
                        payload.orderId(), payload.customerId(), payload.previousStatus(),
                        payload.total(), payload.occurredAt()));
        snapshot.aplicarEvento(payload.newStatus(), payload.total(), payload.occurredAt());
        repository.save(snapshot);
        log.info("Snapshot actualizado: pedido {} -> {} (correlationId={})",
                payload.orderId(), payload.newStatus(), envelope.correlationId());
    }
}
