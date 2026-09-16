package cl.pedidos360.audit.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla exacta del caso: audit_events(id, event_type, entity_id, actor, payload_json, occurred_at). */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 120)
    private String actor;

    // columnDefinition "text" y NO @Lob: en PostgreSQL, Hibernate mapea
    // @Lob String a un large object (tipo oid), que solo puede leerse dentro
    // de una transaccion. El endpoint de listado es de solo lectura y no abre
    // una, asi que fallaba con "Large Objects may not be used in auto-commit
    // mode". text no tiene limite practico de tamaño para este payload.
    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    public AuditEvent(String eventType, Long entityId, String actor, String payloadJson, Instant occurredAt) {
        this.eventType = eventType;
        this.entityId = entityId;
        this.actor = actor == null ? "desconocido" : actor;
        this.payloadJson = payloadJson;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getActor() {
        return actor;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
