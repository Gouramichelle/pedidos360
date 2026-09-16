package cl.pedidos360.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderStatusTest {

    @Test
    @DisplayName("regla clave del caso: no se puede despachar sin haber aceptado")
    void noDespachaSinAceptar() {
        assertThat(OrderStatus.CREADO.puedePasarA(OrderStatus.DESPACHADO)).isFalse();
        assertThat(OrderStatus.CREADO.puedePasarA(OrderStatus.EN_PREPARACION)).isFalse();
    }

    @Test
    @DisplayName("el camino feliz completo es una secuencia valida")
    void caminoFelizCompleto() {
        assertThat(OrderStatus.CREADO.puedePasarA(OrderStatus.ACEPTADO)).isTrue();
        assertThat(OrderStatus.ACEPTADO.puedePasarA(OrderStatus.EN_PREPARACION)).isTrue();
        assertThat(OrderStatus.EN_PREPARACION.puedePasarA(OrderStatus.DESPACHADO)).isTrue();
        assertThat(OrderStatus.DESPACHADO.puedePasarA(OrderStatus.ENTREGADO)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"ENTREGADO", "CANCELADO"})
    @DisplayName("los estados terminales no tienen transiciones salientes")
    void estadosTerminalesSinSalida(OrderStatus terminal) {
        assertThat(terminal.esTerminal()).isTrue();
        assertThat(terminal.siguientesPosibles()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CREADO", "ACEPTADO", "EN_PREPARACION"})
    @DisplayName("un pedido puede cancelarse mientras no haya sido despachado")
    void puedeCancelarseAntesDeDespachar(OrderStatus estado) {
        assertThat(estado.puedePasarA(OrderStatus.CANCELADO)).isTrue();
    }

    @Test
    @DisplayName("una vez despachado, el pedido ya no puede cancelarse (solo llegar a ENTREGADO)")
    void noSeCancelaUnPedidoYaDespachado() {
        assertThat(OrderStatus.DESPACHADO.puedePasarA(OrderStatus.CANCELADO)).isFalse();
        assertThat(OrderStatus.DESPACHADO.siguientesPosibles()).containsExactly(OrderStatus.ENTREGADO);
    }

    @Test
    @DisplayName("cada estado activo tiene un evento de dominio asociado")
    void eventTypeNoNulo() {
        for (OrderStatus s : OrderStatus.values()) {
            assertThat(s.eventType()).isNotBlank();
        }
        assertThat(OrderStatus.ACEPTADO.eventType()).isEqualTo("OrderAccepted");
    }
}
