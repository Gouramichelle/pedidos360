package cl.pedidos360.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void nacePendienteYSinTimestampsDeEstadosFuturos() {
        Order order = new Order("cliente-1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREADO);
        assertThat(order.getAcceptedAt()).isNull();
        assertThat(order.getCreatedAt()).isNotNull();
    }

    @Test
    void calculaElTotalComoSumaDeSubtotales() {
        Order order = new Order("cliente-1");
        order.addItem(new OrderItem(1L, "SKU-A", 2, new BigDecimal("1000.00")));
        order.addItem(new OrderItem(2L, "SKU-B", 1, new BigDecimal("500.00")));
        assertThat(order.total()).isEqualByComparingTo("2500.00");
    }

    @Test
    void aceptarUnPedidoRegistraElTimestampYDevuelveElEstadoAnterior() {
        Order order = new Order("cliente-1");
        OrderStatus anterior = order.cambiarEstado(OrderStatus.ACEPTADO);
        assertThat(anterior).isEqualTo(OrderStatus.CREADO);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACEPTADO);
        assertThat(order.getAcceptedAt()).isNotNull();
    }

    @Test
    void rechazaDespacharUnPedidoRecienCreado() {
        Order order = new Order("cliente-1");
        assertThatThrownBy(() -> order.cambiarEstado(OrderStatus.DESPACHADO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transicion invalida");
    }

    @Test
    void rechazaReenviarAlMismoEstado() {
        Order order = new Order("cliente-1");
        order.cambiarEstado(OrderStatus.ACEPTADO);
        assertThatThrownBy(() -> order.cambiarEstado(OrderStatus.ACEPTADO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya esta en estado");
    }

    @Test
    void unPedidoEntregadoNoAceptaNuevasTransiciones() {
        Order order = new Order("cliente-1");
        order.cambiarEstado(OrderStatus.ACEPTADO);
        order.cambiarEstado(OrderStatus.EN_PREPARACION);
        order.cambiarEstado(OrderStatus.DESPACHADO);
        order.cambiarEstado(OrderStatus.ENTREGADO);
        assertThatThrownBy(() -> order.cambiarEstado(OrderStatus.CANCELADO))
                .isInstanceOf(IllegalStateException.class);
    }
}
