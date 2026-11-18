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

    /**
     * Quien toco cada pedido por ultima vez y desde donde.
     *
     * Se alimenta de audit.timeline, no de orders.events: el topico de negocio
     * no lleva el origen de la accion.
     */
    @GetMapping("/activity")
    @PreAuthorize("hasAnyRole('Admin', 'Auditor')")
    public java.util.List<ReportDtos.ActividadPedido> actividad() {
        return service.actividad();
    }
}
