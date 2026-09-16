package cl.pedidos360.orders.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Maquina de estados del pedido.
 *
 * Regla clave del caso: no se puede despachar sin aceptar. Queda garantizada
 * porque DESPACHADO solo es alcanzable desde EN_PREPARACION, y EN_PREPARACION
 * solo desde ACEPTADO.
 */
public enum OrderStatus {

    CREADO("OrderCreated"),
    ACEPTADO("OrderAccepted"),
    EN_PREPARACION("OrderPreparing"),
    DESPACHADO("OrderDispatched"),
    ENTREGADO("OrderDelivered"),
    CANCELADO("OrderCancelled");

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSICIONES = Map.of(
            CREADO, EnumSet.of(ACEPTADO, CANCELADO),
            ACEPTADO, EnumSet.of(EN_PREPARACION, CANCELADO),
            EN_PREPARACION, EnumSet.of(DESPACHADO, CANCELADO),
            DESPACHADO, EnumSet.of(ENTREGADO),
            ENTREGADO, EnumSet.noneOf(OrderStatus.class),
            CANCELADO, EnumSet.noneOf(OrderStatus.class));

    /** Nombre del evento publicado a Kafka al entrar en este estado. */
    private final String eventType;

    OrderStatus(String eventType) {
        this.eventType = eventType;
    }

    public String eventType() {
        return eventType;
    }

    public boolean puedePasarA(OrderStatus destino) {
        return TRANSICIONES.get(this).contains(destino);
    }

    public Set<OrderStatus> siguientesPosibles() {
        return TRANSICIONES.get(this);
    }

    public boolean esTerminal() {
        return TRANSICIONES.get(this).isEmpty();
    }
}
