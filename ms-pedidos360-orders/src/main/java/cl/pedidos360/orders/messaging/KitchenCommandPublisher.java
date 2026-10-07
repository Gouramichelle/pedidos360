package cl.pedidos360.orders.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import cl.pedidos360.orders.config.MensajeriaProperties;
import cl.pedidos360.orders.domain.Order;

/**
 * Comando de emision del ticket de cocina, al aceptar el pedido.
 *
 * Va por el exchange DIRECT y no por el topic: un ticket de cocina tiene un
 * unico destinatario y una clave exacta, no hay variantes ni patrones. Es el
 * caso que justifica tener los dos tipos de exchange en vez de uno solo.
 */
@Component
public class KitchenCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(KitchenCommandPublisher.class);
    private static final String FLUJO = "kitchen";

    private final RabbitTemplate rabbitTemplate;
    private final MensajeriaProperties mensajeria;

    public KitchenCommandPublisher(RabbitTemplate rabbitTemplate, MensajeriaProperties mensajeria) {
        this.rabbitTemplate = rabbitTemplate;
        this.mensajeria = mensajeria;
    }

    public void emitirTicket(Order order, String traceId, String correlationId) {
        MensajeriaProperties.Flujo flujo = mensajeria.flujo(FLUJO);
        KitchenCommandPayload payload = new KitchenCommandPayload(
                order.getId(), order.getCustomerId(), order.getItems().size());
        EventEnvelope<KitchenCommandPayload> envelope = EventEnvelope.of(
                "KitchenTicketRequested", traceId, correlationId, payload);
        try {
            rabbitTemplate.convertAndSend(mensajeria.exchanges().direct(), flujo.routingKey(), envelope);
            log.info("Encolado ticket de cocina para el pedido {} ({} items), correlationId={}",
                    order.getId(), payload.cantidadItems(), correlationId);
        } catch (Exception ex) {
            log.error("No se pudo encolar el ticket de cocina del pedido {}: {}",
                    order.getId(), ex.getMessage(), ex);
        }
    }
}
