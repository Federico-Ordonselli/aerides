package dev.ordonselli.raidboard.audit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findAllByGroupIdOrderByIdAsc(Long groupId);
}
