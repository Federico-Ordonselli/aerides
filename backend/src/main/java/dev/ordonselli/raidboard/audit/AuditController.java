package dev.ordonselli.raidboard.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.ordonselli.raidboard.common.web.PageResponse;

@RestController
@RequestMapping("/api/groups/{groupId}/audit")
@Tag(name = "Audit log")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OFFICER')")
    @Operation(summary = "The group's audit log, newest first, optionally filtered by action (OFFICER or OWNER)")
    public PageResponse<AuditEntryResponse> list(@PathVariable Long groupId,
            @RequestParam(required = false) AuditAction action,
            @PageableDefault(size = 20) Pageable pageable) {
        // Only page and size come from the client: a log is always read newest first
        return auditService.findForGroup(groupId, action,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
    }
}
