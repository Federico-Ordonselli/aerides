package dev.ordonselli.aerides.audit;

import java.time.Instant;
import java.util.Map;

/**
 * One audit log line for the audit page.
 *
 * @param actorName display name of the user who made the change; null if that account was deleted
 */
public record AuditEntryResponse(Long id, AuditAction action, AuditEntityType entityType, Long entityId,
        Long actorId, String actorName, Map<String, Object> details, Instant createdAt) {
}
