package cl.pedidos360.audit.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByEntityIdOrderByOccurredAtAsc(Long entityId);

    List<AuditEvent> findByActorOrderByOccurredAtDesc(String actor);

    List<AuditEvent> findByOccurredAtBetweenOrderByOccurredAtDesc(Instant from, Instant to);

    List<AuditEvent> findAllByOrderByOccurredAtDesc();
}
