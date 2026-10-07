package cl.pedidos360.orders.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import cl.pedidos360.orders.config.MensajeriaProperties;
import cl.pedidos360.orders.domain.Order;

/**
 * Comando de generacion del documento de cobro, al entregar el pedido.
 *
 * Va por el exchange TOPIC, igual que el correo: el dia que haya variantes de
 * documento ("invoice.gen.boleta", "invoice.gen.factura") el patron
 * "invoice.*" las recibe sin cambiar la topologia.
 */
@Component
public class InvoiceCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(InvoiceCommandPublisher.class);
    private static final String FLUJO = "invoice";

    private final RabbitTemplate rabbitTemplate;
    private final MensajeriaProperties mensajeria;

    public InvoiceCommandPublisher(RabbitTemplate rabbitTemplate, MensajeriaProperties mensajeria) {
        this.rabbitTemplate = rabbitTemplate;
        this.mensajeria = mensajeria;
    }

    public void generarDocumento(Order order, String traceId, String correlationId) {
        MensajeriaProperties.Flujo flujo = mensajeria.flujo(FLUJO);
        InvoiceCommandPayload payload = new InvoiceCommandPayload(
                order.getId(), order.getCustomerId(), order.total());
        EventEnvelope<InvoiceCommandPayload> envelope = EventEnvelope.of(
                "InvoiceGenerationRequested", traceId, correlationId, payload);
        try {
            rabbitTemplate.convertAndSend(mensajeria.exchanges().topic(), flujo.routingKey(), envelope);
            log.info("Encolada generacion de documento para el pedido {} (total {}), correlationId={}",
                    order.getId(), payload.total(), correlationId);
        } catch (Exception ex) {
            log.error("No se pudo encolar el documento del pedido {}: {}",
                    order.getId(), ex.getMessage(), ex);
        }
    }
}
