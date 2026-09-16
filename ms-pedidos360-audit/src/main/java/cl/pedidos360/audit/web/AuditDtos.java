package cl.pedidos360.audit.web;

import java.time.Instant;

import cl.pedidos360.audit.domain.AuditEvent;

public class AuditDtos {

    public record AuditEventResponse(
            Long id, String eventType, Long entityId, String actor, String payloadJson, Instant occurredAt) {

        public static AuditEventResponse from(AuditEvent e) {
            return new AuditEventResponse(e.getId(), e.getEventType(), e.getEntityId(), e.getActor(), e.getPayloadJson(), e.getOccurredAt());
        }
    }
}
