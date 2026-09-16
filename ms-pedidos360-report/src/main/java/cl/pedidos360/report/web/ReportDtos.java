package cl.pedidos360.report.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ReportDtos {

    public record SalesByHourPoint(Instant hour, BigDecimal total) {
    }

    public record ActiveStatusCount(String status, long count) {
    }

    public record KpiSummary(
            List<SalesByHourPoint> salesByHour,
            Double averageLeadTimeSeconds,
            List<ActiveStatusCount> activeStatusCounts,
            Map<String, Long> statusBreakdown) {
    }
}
