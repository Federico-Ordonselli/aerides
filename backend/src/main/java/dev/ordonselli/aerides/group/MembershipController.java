package dev.ordonselli.aerides.group;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.ordonselli.aerides.common.security.CurrentUserId;

@RestController
@RequestMapping("/api/groups/{groupId}/members")
@Tag(name = "Group members")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping
    @PreAuthorize("@groupSecurity.isMember(#groupId)")
    @Operation(summary = "List the members of a group")
    public List<MemberResponse> list(@PathVariable Long groupId) {
        return membershipService.listMembers(groupId);
    }

    // "/me" is a literal path, so Spring prefers it over "/{userId}"
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@groupSecurity.isMember(#groupId)")
    @Operation(summary = "Leave the group (not allowed for the OWNER)")
    public void leave(@PathVariable Long groupId, @CurrentUserId Long userId) {
        membershipService.leave(groupId, userId);
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OWNER')")
    @Operation(summary = "Change the role of a member to MEMBER or OFFICER (OWNER)")
    public MemberResponse changeRole(@PathVariable Long groupId, @PathVariable("userId") Long targetUserId,
            @CurrentUserId Long actorId, @Valid @RequestBody ChangeRoleRequest request) {
        return membershipService.changeRole(groupId, actorId, targetUserId, request.role());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OWNER')")
    @Operation(summary = "Remove a member from the group (OWNER)")
    public void remove(@PathVariable Long groupId, @PathVariable("userId") Long targetUserId,
            @CurrentUserId Long actorId) {
        membershipService.removeMember(groupId, actorId, targetUserId);
    }
}
