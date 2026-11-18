package cl.pedidos360.report.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class OrderActivityTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void registraLaPrimeraIntervencion() {
        OrderActivity a = new OrderActivity(1L);
        a.registrar("OrderCreated", "cliente@duocuc.cl", "web", "CREADO", T0);

        assertThat(a.getLastActor()).isEqualTo("cliente@duocuc.cl");
        assertThat(a.getLastSource()).isEqualTo("web");
        assertThat(a.getLastStatus()).isEqualTo("CREADO");
        assertThat(a.getTransitions()).isEqualTo(1);
    }

    @Test
    void cuentaCadaIntervencionYSeQuedaConLaUltima() {
        OrderActivity a = new OrderActivity(1L);
        a.registrar("OrderCreated", "cliente", "web", "CREADO", T0);
        a.registrar("OrderStatusChanged", "operador", "backoffice", "ACEPTADO", T0.plusSeconds(60));

        assertThat(a.getTransitions()).isEqualTo(2);
        assertThat(a.getLastStatus()).isEqualTo("ACEPTADO");
        assertThat(a.getLastActor()).isEqualTo("operador");
    }

    /**
     * Un topico compactado puede reentregar un mensaje anterior despues de una
     * recuperacion. La ultima actividad no deberia retroceder en el tiempo.
     */
    @Test
    void ignoraUnMensajeMasViejoQueElYaRegistrado() {
        OrderActivity a = new OrderActivity(1L);
        a.registrar("OrderStatusChanged", "operador", "backoffice", "ACEPTADO", T0.plusSeconds(60));
        a.registrar("OrderCreated", "cliente", "web", "CREADO", T0);

        assertThat(a.getLastStatus()).isEqualTo("ACEPTADO");
        assertThat(a.getTransitions())
                .as("un mensaje viejo no deberia contar como una intervencion nueva")
                .isEqualTo(1);
    }
}
