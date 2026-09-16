package cl.pedidos360.report.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo lectura, solo Admin: asi lo define el caso para la pantalla /reports. */
@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/kpis")
    @PreAuthorize("hasRole('Admin')")
    public ReportDtos.KpiSummary kpis() {
        return service.kpis();
    }
}
