package cl.pedidos360.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class AuditEventTest {

    @Test
    void unActorNuloSeRegistraComoDesconocidoEnVezDeGuardarNull() {
        AuditEvent event = new AuditEvent("OrderCreated", 1L, null, "{}", Instant.now());
        assertThat(event.getActor()).isEqualTo("desconocido");
    }

    @Test
    void conservaLosDatosDelEvento() {
        Instant ahora = Instant.now();
        AuditEvent event = new AuditEvent("OrderAccepted", 42L, "operador@pedidos360.cl", "{\"x\":1}", ahora);
        assertThat(event.getEventType()).isEqualTo("OrderAccepted");
        assertThat(event.getEntityId()).isEqualTo(42L);
        assertThat(event.getActor()).isEqualTo("operador@pedidos360.cl");
        assertThat(event.getPayloadJson()).isEqualTo("{\"x\":1}");
        assertThat(event.getOccurredAt()).isEqualTo(ahora);
    }
}
