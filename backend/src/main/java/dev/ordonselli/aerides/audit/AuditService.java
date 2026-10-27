package dev.ordonselli.aerides.audit;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.ordonselli.aerides.common.web.PageResponse;
import dev.ordonselli.aerides.user.User;
import dev.ordonselli.aerides.user.UserRepository;

/**
 * Writes audit entries. Services call it explicitly, right after the change they describe.
 * <p>
 * {@code Propagation.MANDATORY} makes it fail if there is no transaction already running: the entry
 * must be saved in the same transaction as the change, so either both are committed or neither is.
 */
@Service
public class AuditService {

    private final AuditEntryRepository auditEntryRepository;
    private final UserRepository userRepository;

    public AuditService(AuditEntryRepository auditEntryRepository, UserRepository userRepository) {
        this.auditEntryRepository = auditEntryRepository;
        this.userRepository = userRepository;
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

    /**
     * One page of a group's log, newest first. The actors' names are loaded with a single query for
     * the whole page (entries keep only the actor id, see {@link AuditEntry}).
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> findForGroup(Long groupId, AuditAction action, Pageable pageable) {
        Page<AuditEntry> page = action == null
                ? auditEntryRepository.findAllByGroupIdOrderByIdDesc(groupId, pageable)
                : auditEntryRepository.findAllByGroupIdAndActionOrderByIdDesc(groupId, action, pageable);
        Map<Long, String> actorNames = userRepository.findAllById(page.map(AuditEntry::getActorId)
                        .filter(Objects::nonNull).toSet()).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
        return PageResponse.of(page.map(entry -> new AuditEntryResponse(entry.getId(), entry.getAction(),
                entry.getEntityType(), entry.getEntityId(), entry.getActorId(), actorNames.get(entry.getActorId()),
                entry.getDetails(), entry.getCreatedAt())));
    }
}
