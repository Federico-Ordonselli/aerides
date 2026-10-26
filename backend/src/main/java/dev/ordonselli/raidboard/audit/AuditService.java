package dev.ordonselli.raidboard.audit;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit entries. Services call it explicitly, right after the change they describe.
 * <p>
 * {@code Propagation.MANDATORY} makes it fail if there is no transaction already running: the entry
 * must be saved in the same transaction as the change, so either both are committed or neither is.
 */
@Service
public class AuditService {

    private final AuditEntryRepository auditEntryRepository;

    public AuditService(AuditEntryRepository auditEntryRepository) {
        this.auditEntryRepository = auditEntryRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long groupId, Long actorId, AuditAction action, AuditEntityType entityType, Long entityId,
            Map<String, Object> details) {
        auditEntryRepository.save(new AuditEntry(groupId, actorId, action, entityType, entityId, details));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long groupId, Long actorId, AuditAction action, AuditEntityType entityType, Long entityId) {
        record(groupId, actorId, action, entityType, entityId, Map.of());
    }
}
