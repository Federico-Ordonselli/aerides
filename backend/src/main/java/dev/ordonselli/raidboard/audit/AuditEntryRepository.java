package dev.ordonselli.raidboard.audit;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findAllByGroupIdOrderByIdAsc(Long groupId);

    // Ordered by id, not created_at: ids grow with every insert, and two entries written by the same
    // transaction can have the same timestamp
    Page<AuditEntry> findAllByGroupIdOrderByIdDesc(Long groupId, Pageable pageable);

    Page<AuditEntry> findAllByGroupIdAndActionOrderByIdDesc(Long groupId, AuditAction action, Pageable pageable);
}
