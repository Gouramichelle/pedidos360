package cl.pedidos360.report.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cl.pedidos360.report.domain.OrderActivity;
import cl.pedidos360.report.domain.OrderActivityRepository;

/**
 * Consume audit.timeline y mantiene al dia quien toco cada pedido por ultima
 * vez, desde donde y cuantas veces se intervino.
 *
 * Es el segundo caso de uso de streaming del sistema, y lee un topico distinto
 * del de negocio por dos motivos:
 *
 *   - audit.timeline lleva el origen de la accion, que orders.events no trae
 *   - esta compactado por pedido, asi que releerlo entero reconstruye esta
 *     proyeccion sin arrastrar todo el historial de eventos
 *
 * Va en su propio grupo de consumidores. Si compartiera grupo con el listener
 * de orders.events, cada mensaje llegaria a uno solo de los dos y la mitad del
 * trabajo quedaria sin hacer.
 */
@Component
public class AuditTimelineListener {

    private static final Logger log = LoggerFactory.getLogger(AuditTimelineListener.class);

    private final OrderActivityRepository repository;

    public AuditTimelineListener(OrderActivityRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(
            topics = "audit.timeline",
            groupId = "${spring.kafka.consumer.group-id}-timeline",
            containerFactory = "timelineListenerContainerFactory")
    @Transactional
    public void onTimelineEvent(AuditTimelinePayload payload) {
        // Un mensaje sin pedido no se puede proyectar y reintentarlo daria lo
        // mismo: se descarta para que el error handler lo mande al DLT.
        if (payload == null || payload.orderId() == null || payload.occurredAt() == null) {
            throw new IllegalArgumentException("Registro de linea de tiempo sin pedido o sin fecha");
        }

        OrderActivity actividad = repository.findById(payload.orderId())
                .orElseGet(() -> new OrderActivity(payload.orderId()));
        actividad.registrar(
                payload.eventType(),
                payload.actor(),
                payload.source(),
                payload.newStatus(),
                payload.occurredAt());
        repository.save(actividad);

        log.info("Actividad registrada: pedido {} tocado por {} desde {} ({} -> {})",
                payload.orderId(), payload.actor(), payload.source(),
                payload.previousStatus(), payload.newStatus());
    }
}
