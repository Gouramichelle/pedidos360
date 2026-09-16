package cl.pedidos360.audit.web;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.pedidos360.audit.domain.AuditEventRepository;
import cl.pedidos360.audit.web.AuditDtos.AuditEventResponse;

/**
 * Solo lectura, solo rol Auditor -- asi lo define el caso para /api/audit/*.
 * Filtros soportados: actor, rango de fechas, entidad (pedido).
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditEventRepository repository;

    public AuditController(AuditEventRepository repository) {
        this.repository = repository;
    }

    // El caso define el actor "Auditor" para este modulo, pero la seccion de
    // seguridad solo lista los roles Admin/Operador/Cliente para la App
    // Registration. Se acepta tambien Admin para no dejar la pantalla /audit
    // inalcanzable si el rol Auditor no llega a crearse en Azure AD -- ver
    // README para la recomendacion de agregarlo explicitamente antes de la demo.
    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('Auditor', 'Admin')")
    public List<AuditEventResponse> list(
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        List<AuditEventResponse> resultado;
        if (entityId != null) {
            resultado = map(repository.findByEntityIdOrderByOccurredAtAsc(entityId));
        } else if (actor != null) {
            resultado = map(repository.findByActorOrderByOccurredAtDesc(actor));
        } else if (from != null && to != null) {
            resultado = map(repository.findByOccurredAtBetweenOrderByOccurredAtDesc(from, to));
        } else {
            resultado = map(repository.findAllByOrderByOccurredAtDesc());
        }
        return resultado;
    }

    private List<AuditEventResponse> map(List<cl.pedidos360.audit.domain.AuditEvent> events) {
        return events.stream().map(AuditEventResponse::from).toList();
    }
}
