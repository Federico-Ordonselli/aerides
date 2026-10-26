package dev.ordonselli.raidboard.group;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.ordonselli.raidboard.common.security.CurrentUserId;

@RestController
@RequestMapping("/api/groups")
@Tag(name = "Groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a group: the caller becomes its OWNER")
    public GroupDetailResponse create(@CurrentUserId Long userId, @Valid @RequestBody CreateGroupRequest request) {
        return groupService.create(userId, request);
    }

    @GetMapping
    @Operation(summary = "List the groups of the caller, with their role in each")
    public List<GroupSummaryResponse> listMine(@CurrentUserId Long userId) {
        return groupService.listForUser(userId);
    }

    @PostMapping("/join")
    @Operation(summary = "Join a group with its invite code")
    public GroupDetailResponse join(@CurrentUserId Long userId, @Valid @RequestBody JoinGroupRequest request) {
        return groupService.join(userId, request);
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("@groupSecurity.isMember(#groupId)")
    @Operation(summary = "Get a group (members only)")
    public GroupDetailResponse get(@PathVariable Long groupId, @CurrentUserId Long userId) {
        return groupService.get(groupId, userId);
    }

    @PatchMapping("/{groupId}")
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OFFICER')")
    @Operation(summary = "Update name and description (OFFICER or OWNER)")
    public GroupDetailResponse update(@PathVariable Long groupId, @CurrentUserId Long userId,
            @Valid @RequestBody UpdateGroupRequest request) {
        return groupService.update(groupId, userId, request);
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OWNER')")
    @Operation(summary = "Delete the group with all its data (OWNER)")
    public void delete(@PathVariable Long groupId, @CurrentUserId Long userId) {
        groupService.delete(groupId, userId);
    }

    @PostMapping("/{groupId}/invite-code")
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OWNER')")
    @Operation(summary = "Generate a new invite code; the old one stops working (OWNER)")
    public InviteCodeResponse regenerateInviteCode(@PathVariable Long groupId, @CurrentUserId Long userId) {
        return groupService.regenerateInviteCode(groupId, userId);
    }

    @PutMapping("/{groupId}/discord-webhook")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OWNER')")
    @Operation(summary = "Set or remove (url = null) the Discord webhook (OWNER)")
    public void updateDiscordWebhook(@PathVariable Long groupId, @CurrentUserId Long userId,
            @Valid @RequestBody DiscordWebhookRequest request) {
        groupService.updateDiscordWebhook(groupId, userId, request);
    }
}
