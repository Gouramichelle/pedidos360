package cl.pedidos360.report.web;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import cl.pedidos360.report.domain.OrderSnapshot;
import cl.pedidos360.report.domain.OrderSnapshotRepository;
import cl.pedidos360.report.web.ReportDtos.ActiveStatusCount;
import cl.pedidos360.report.web.ReportDtos.KpiSummary;
import cl.pedidos360.report.web.ReportDtos.SalesByHourPoint;

/**
 * KPIs pedidos por el caso: ventas por hora, lead time, estados activos.
 * La agregacion se hace en memoria sobre la proyeccion order_snapshots en vez
 * de SQL nativo -- son unas pocas miles de filas para el volumen de un MVP, y
 * mantiene el codigo portable entre el H2 de test y el PostgreSQL de RDS.
 */
@Service
public class ReportService {

    private static final String ESTADO_ENTREGADO = "ENTREGADO";
    private static final List<String> ESTADOS_ACTIVOS =
            List.of("CREADO", "ACEPTADO", "EN_PREPARACION", "DESPACHADO");

    private final OrderSnapshotRepository repository;

    public ReportService(OrderSnapshotRepository repository) {
        this.repository = repository;
    }

    public KpiSummary kpis() {
        List<OrderSnapshot> todos = repository.findAll();
        List<OrderSnapshot> entregados = todos.stream()
                .filter(s -> ESTADO_ENTREGADO.equals(s.getCurrentStatus()) && s.getDeliveredAt() != null)
                .toList();

        return new KpiSummary(
                ventasPorHora(entregados),
                leadTimePromedioSegundos(entregados),
                estadosActivos(todos),
                desgloseCompleto(todos));
    }

    private List<SalesByHourPoint> ventasPorHora(List<OrderSnapshot> entregados) {
        Map<Instant, BigDecimal> porHora = new TreeMap<>();
        for (OrderSnapshot s : entregados) {
            Instant hora = s.getDeliveredAt().truncatedTo(ChronoUnit.HOURS).atZone(ZoneOffset.UTC).toInstant();
            porHora.merge(hora, s.getTotal(), BigDecimal::add);
        }
        return porHora.entrySet().stream()
                .map(e -> new SalesByHourPoint(e.getKey(), e.getValue()))
                .toList();
    }

    private Double leadTimePromedioSegundos(List<OrderSnapshot> entregados) {
        if (entregados.isEmpty()) {
            return null;
        }
        return entregados.stream()
                .mapToLong(s -> Duration.between(s.getCreatedAt(), s.getDeliveredAt()).getSeconds())
                .average()
                .orElse(0.0);
    }

    private List<ActiveStatusCount> estadosActivos(List<OrderSnapshot> todos) {
        Map<String, Long> conteo = todos.stream()
                .filter(s -> ESTADOS_ACTIVOS.contains(s.getCurrentStatus()))
                .collect(Collectors.groupingBy(OrderSnapshot::getCurrentStatus, Collectors.counting()));
        return ESTADOS_ACTIVOS.stream()
                .map(estado -> new ActiveStatusCount(estado, conteo.getOrDefault(estado, 0L)))
                .sorted(Comparator.comparing(ActiveStatusCount::status))
                .toList();
    }

    private Map<String, Long> desgloseCompleto(List<OrderSnapshot> todos) {
        return todos.stream()
                .collect(Collectors.groupingBy(OrderSnapshot::getCurrentStatus, Collectors.counting()));
    }
}
