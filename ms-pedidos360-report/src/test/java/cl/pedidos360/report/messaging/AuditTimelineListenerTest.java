package cl.pedidos360.report.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.pedidos360.report.domain.OrderActivity;
import cl.pedidos360.report.domain.OrderActivityRepository;

@ExtendWith(MockitoExtension.class)
class AuditTimelineListenerTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    OrderActivityRepository repository;

    private AuditTimelinePayload registro(Long orderId, Instant cuando) {
        return new AuditTimelinePayload(orderId, "OrderStatusChanged", "operador",
                "CREADO", "ACEPTADO", cuando, "backoffice");
    }

    @Test
    void proyectaUnRegistroNuevo() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        new AuditTimelineListener(repository).onTimelineEvent(registro(7L, T0));

        ArgumentCaptor<OrderActivity> guardado = ArgumentCaptor.forClass(OrderActivity.class);
        verify(repository).save(guardado.capture());
        assertThat(guardado.getValue().getOrderId()).isEqualTo(7L);
        assertThat(guardado.getValue().getLastSource()).isEqualTo("backoffice");
    }

    /**
     * Sin pedido no hay nada que proyectar, y reintentarlo daria lo mismo. Se
     * rechaza para que el error handler lo mande al DLT en vez de reintentar
     * tres veces en balde.
     */
    @Test
    void rechazaUnRegistroSinPedido() {
        AuditTimelineListener listener = new AuditTimelineListener(repository);

        assertThatThrownBy(() -> listener.onTimelineEvent(registro(null, T0)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rechazaUnRegistroSinFecha() {
        AuditTimelineListener listener = new AuditTimelineListener(repository);

        assertThatThrownBy(() -> listener.onTimelineEvent(registro(7L, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }
}
