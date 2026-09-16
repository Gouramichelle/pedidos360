package cl.pedidos360.report.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.pedidos360.report.domain.OrderSnapshot;
import cl.pedidos360.report.domain.OrderSnapshotRepository;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    OrderSnapshotRepository repository;

    private OrderSnapshot entregado(long id, Instant creado, long minutosParaEntregar, BigDecimal total) {
        OrderSnapshot s = new OrderSnapshot(id, "cliente-" + id, "CREADO", total, creado);
        s.aplicarEvento("ENTREGADO", total, creado.plus(minutosParaEntregar, ChronoUnit.MINUTES));
        return s;
    }

    private OrderSnapshot activo(long id, String estado) {
        return new OrderSnapshot(id, "cliente-" + id, estado, new BigDecimal("100.00"), Instant.now());
    }

    @Test
    void calculaElLeadTimePromedioEnSegundos() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        when(repository.findAll()).thenReturn(List.of(
                entregado(1, base, 10, new BigDecimal("100.00")),
                entregado(2, base, 20, new BigDecimal("200.00"))));

        ReportService service = new ReportService(repository);
        Double leadTime = service.kpis().averageLeadTimeSeconds();

        assertThat(leadTime).isEqualTo(15 * 60.0); // promedio de 10 y 20 minutos
    }

    @Test
    void sumaLasVentasAgrupadasPorHoraDeEntrega() {
        Instant base = Instant.parse("2026-01-01T10:05:00Z");
        when(repository.findAll()).thenReturn(List.of(
                entregado(1, base, 2, new BigDecimal("100.00")),
                entregado(2, base, 3, new BigDecimal("50.00"))));

        var puntos = new ReportService(repository).kpis().salesByHour();

        assertThat(puntos).hasSize(1);
        assertThat(puntos.get(0).total()).isEqualByComparingTo("150.00");
    }

    @Test
    void cuentaLosPedidosPorEstadoActivoYExcluyeTerminales() {
        when(repository.findAll()).thenReturn(List.of(
                activo(1, "CREADO"),
                activo(2, "ACEPTADO"),
                activo(3, "ACEPTADO"),
                activo(4, "ENTREGADO"),
                activo(5, "CANCELADO")));

        var conteo = new ReportService(repository).kpis().activeStatusCounts();

        assertThat(conteo).extracting("status", "count")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("ACEPTADO", 2L),
                        org.assertj.core.groups.Tuple.tuple("CREADO", 1L),
                        org.assertj.core.groups.Tuple.tuple("DESPACHADO", 0L),
                        org.assertj.core.groups.Tuple.tuple("EN_PREPARACION", 0L));
    }

    @Test
    void sinPedidosEntregadosElLeadTimeEsNulo() {
        when(repository.findAll()).thenReturn(List.of(activo(1, "CREADO")));
        assertThat(new ReportService(repository).kpis().averageLeadTimeSeconds()).isNull();
    }
}
