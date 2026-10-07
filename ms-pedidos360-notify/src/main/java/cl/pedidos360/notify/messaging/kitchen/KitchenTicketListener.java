package cl.pedidos360.notify.messaging.kitchen;

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
 * Dominio cocina: emite el ticket de preparacion cuando se acepta un pedido.
 *
 * Los mensajes llegan por el exchange DIRECT, con la routing key exacta
 * configurada para este flujo. La confirmacion la resuelve
 * ConfirmacionDeMensajes, igual que en los otros dos dominios.
 */
@Component
public class KitchenTicketListener {

    private static final Logger log = LoggerFactory.getLogger(KitchenTicketListener.class);
    private static final String COLA = "kitchen";

    private final Set<String> procesados = ConcurrentHashMap.newKeySet();
    private final ConfirmacionDeMensajes confirmacion;

    public KitchenTicketListener(ConfirmacionDeMensajes confirmacion) {
        this.confirmacion = confirmacion;
    }

    @RabbitListener(queues = "${mensajeria.flujos.kitchen.cola}")
    public void onKitchenCommand(EventEnvelope<KitchenCommandPayload> envelope,
            Channel canal,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

        String eventId = envelope == null ? null : envelope.eventId();
        confirmacion.procesar(COLA, eventId, canal, deliveryTag, () -> emitirTicket(envelope));
    }

    private void emitirTicket(EventEnvelope<KitchenCommandPayload> envelope) {
        validar(envelope);

        if (procesados.contains(envelope.eventId())) {
            log.info("Ticket {} ya emitido antes, se descarta (idempotencia)", envelope.eventId());
            return;
        }

        KitchenCommandPayload payload = envelope.payload();
        log.info("Ticket de cocina para el pedido {} del cliente {}: {} items (correlationId={})",
                payload.orderId(), payload.customerId(), payload.cantidadItems(), envelope.correlationId());

        // La impresion real del ticket queda fuera de alcance. Punto de
        // extension: la llamada a la impresora o al sistema de cocina va aqui,
        // y sus fallos de red deben envolverse en ErrorRecuperable.

        procesados.add(envelope.eventId());
    }

    private void validar(EventEnvelope<KitchenCommandPayload> envelope) {
        if (envelope == null || envelope.eventId() == null || envelope.eventId().isBlank()) {
            throw new ErrorNoRecuperable("El mensaje llego sin eventId, no se puede procesar ni deduplicar");
        }
        KitchenCommandPayload payload = envelope.payload();
        if (payload == null) {
            throw new ErrorNoRecuperable("El evento " + envelope.eventId() + " llego sin payload");
        }
        if (payload.orderId() == null) {
            throw new ErrorNoRecuperable("El evento " + envelope.eventId() + " no indica a que pedido corresponde");
        }
        if (payload.cantidadItems() <= 0) {
            throw new ErrorNoRecuperable(
                    "El evento " + envelope.eventId() + " pide un ticket sin items, no hay nada que preparar");
        }
    }
}
