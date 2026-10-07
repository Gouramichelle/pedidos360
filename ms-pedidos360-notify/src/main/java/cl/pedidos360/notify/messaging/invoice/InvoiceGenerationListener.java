package cl.pedidos360.notify.messaging.invoice;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.pedidos360.notify.messaging.comun.ConfirmacionDeMensajes;
import cl.pedidos360.notify.messaging.comun.ErrorNoRecuperable;
import cl.pedidos360.notify.messaging.comun.EventEnvelope;

/**
 * Dominio facturacion: genera el documento de cobro cuando se entrega un
 * pedido.
 *
 * Los mensajes llegan por el exchange TOPIC, con el patron configurado para
 * este flujo, de modo que variantes futuras del documento se reciben sin
 * cambiar la topologia.
 */
@Component
public class InvoiceGenerationListener {

    private static final Logger log = LoggerFactory.getLogger(InvoiceGenerationListener.class);
    private static final String COLA = "invoice";

    private final Set<String> procesados = ConcurrentHashMap.newKeySet();
    private final ConfirmacionDeMensajes confirmacion;

    public InvoiceGenerationListener(ConfirmacionDeMensajes confirmacion) {
        this.confirmacion = confirmacion;
    }

    @RabbitListener(queues = "${mensajeria.flujos.invoice.cola}")
    public void onInvoiceCommand(EventEnvelope<InvoiceCommandPayload> envelope,
            Channel canal,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

        String eventId = envelope == null ? null : envelope.eventId();
        confirmacion.procesar(COLA, eventId, canal, deliveryTag, () -> generarDocumento(envelope));
    }

    private void generarDocumento(EventEnvelope<InvoiceCommandPayload> envelope) {
        validar(envelope);

        if (procesados.contains(envelope.eventId())) {
            log.info("Documento {} ya generado antes, se descarta (idempotencia)", envelope.eventId());
            return;
        }

        InvoiceCommandPayload payload = envelope.payload();
        log.info("Documento de cobro del pedido {} para el cliente {}: total {} (correlationId={})",
                payload.orderId(), payload.customerId(), payload.total(), envelope.correlationId());

        // La emision real del documento queda fuera de alcance. Punto de
        // extension: la llamada al servicio de facturacion va aqui, y sus
        // fallos de red o de cuota deben envolverse en ErrorRecuperable.

        procesados.add(envelope.eventId());
    }

    private void validar(EventEnvelope<InvoiceCommandPayload> envelope) {
        if (envelope == null || envelope.eventId() == null || envelope.eventId().isBlank()) {
            throw new ErrorNoRecuperable("El mensaje llego sin eventId, no se puede procesar ni deduplicar");
        }
        InvoiceCommandPayload payload = envelope.payload();
        if (payload == null) {
            throw new ErrorNoRecuperable("El evento " + envelope.eventId() + " llego sin payload");
        }
        if (payload.orderId() == null) {
            throw new ErrorNoRecuperable("El evento " + envelope.eventId() + " no indica a que pedido corresponde");
        }
        if (payload.total() == null || payload.total().compareTo(BigDecimal.ZERO) < 0) {
            throw new ErrorNoRecuperable(
                    "El evento " + envelope.eventId() + " trae un total invalido, no se puede emitir el documento");
        }
    }
}
