export interface AuditEvent {
  id: number;
  eventType: string;
  entityId: number;
  actor: string;
  payloadJson: string;
  occurredAt: string;
}
