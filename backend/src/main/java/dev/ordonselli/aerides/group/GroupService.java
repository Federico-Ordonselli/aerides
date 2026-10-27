package dev.ordonselli.aerides.group;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ordonselli.aerides.audit.AuditAction;
import dev.ordonselli.aerides.audit.AuditEntityType;
import dev.ordonselli.aerides.audit.AuditService;
import dev.ordonselli.aerides.common.error.ConflictException;
import dev.ordonselli.aerides.common.error.NotFoundException;
import dev.ordonselli.aerides.user.UserRepository;

/**
 * Group lifecycle. Role checks happen in the controller ({@code @PreAuthorize}); methods here still
 * load the caller's membership, so a group the caller doesn't belong to is never returned.
 */
@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final SlugGenerator slugGenerator;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final AuditService auditService;

    public GroupService(GroupRepository groupRepository, MembershipRepository membershipRepository,
            UserRepository userRepository, SlugGenerator slugGenerator, InviteCodeGenerator inviteCodeGenerator,
            AuditService auditService) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.slugGenerator = slugGenerator;
        this.inviteCodeGenerator = inviteCodeGenerator;
        this.auditService = auditService;
    }

    @Transactional
    public GroupDetailResponse create(Long userId, CreateGroupRequest request) {
        Group group = groupRepository.save(new Group(request.name().trim(), uniqueSlug(request.name()),
                trimToNull(request.description()), uniqueInviteCode()));
        // getReferenceById returns a proxy without querying: we only need the user's id for the foreign key
        membershipRepository.save(new Membership(userRepository.getReferenceById(userId), group, MembershipRole.OWNER));
        auditService.record(group.getId(), userId, AuditAction.GROUP_CREATED, AuditEntityType.GROUP, group.getId(),
                Map.of("name", group.getName()));
        return GroupDetailResponse.of(group, MembershipRole.OWNER, 1);
    }

    @Transactional(readOnly = true)
    public List<GroupSummaryResponse> listForUser(Long userId) {
        return membershipRepository.findSummariesByUserId(userId);
    }

    @Transactional(readOnly = true)
    public GroupDetailResponse get(Long groupId, Long userId) {
        Membership membership = requireMembership(groupId, userId);
        return GroupDetailResponse.of(membership.getGroup(), membership.getRole(),
                membershipRepository.countByGroupId(groupId));
    }

    @Transactional
    public GroupDetailResponse update(Long groupId, Long userId, UpdateGroupRequest request) {
        Membership membership = requireMembership(groupId, userId);
        Group group = membership.getGroup();
        String name = request.name().trim();
        String description = trimToNull(request.description());

        Map<String, Object> changes = new LinkedHashMap<>();
        if (!name.equals(group.getName())) {
            changes.put("name", Map.of("from", group.getName(), "to", name));
            group.setName(name);
        }
        if (!Objects.equals(description, group.getDescription())) {
            changes.put("description", "changed");
            group.setDescription(description);
        }
        if (!changes.isEmpty()) {
            auditService.record(groupId, userId, AuditAction.GROUP_UPDATED, AuditEntityType.GROUP, groupId, changes);
        }
        // No save() call needed: the group is a managed entity, Hibernate writes the changes at commit
        return GroupDetailResponse.of(group, membership.getRole(), membershipRepository.countByGroupId(groupId));
    }

    @Transactional
    public void delete(Long groupId, Long userId) {
        Group group = requireMembership(groupId, userId).getGroup();
        auditService.record(groupId, userId, AuditAction.GROUP_DELETED, AuditEntityType.GROUP, groupId,
                Map.of("name", group.getName()));
        // Memberships, games and events are removed by ON DELETE CASCADE in the database
        groupRepository.delete(group);
    }

    @Transactional
    public InviteCodeResponse regenerateInviteCode(Long groupId, Long userId) {
        Group group = requireMembership(groupId, userId).getGroup();
        group.setInviteCode(uniqueInviteCode());
        auditService.record(groupId, userId, AuditAction.INVITE_CODE_REGENERATED, AuditEntityType.GROUP, groupId);
        return new InviteCodeResponse(group.getInviteCode());
    }

    @Transactional
    public void updateDiscordWebhook(Long groupId, Long userId, DiscordWebhookRequest request) {
        Group group = requireMembership(groupId, userId).getGroup();
        group.setDiscordWebhookUrl(trimToNull(request.url()));
        // Never write the URL itself in the audit log: it is a secret
        auditService.record(groupId, userId, AuditAction.DISCORD_WEBHOOK_UPDATED, AuditEntityType.GROUP, groupId,
                Map.of("configured", group.getDiscordWebhookUrl() != null));
    }

    @Transactional
    public GroupDetailResponse join(Long userId, JoinGroupRequest request) {
        String code = request.inviteCode().trim().toUpperCase(Locale.ROOT);
        Group group = groupRepository.findByInviteCode(code)
                .orElseThrow(() -> new NotFoundException("Invalid invite code"));
        if (membershipRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
            throw new ConflictException("You are already a member of this group");
        }
        Membership membership = membershipRepository.save(
                new Membership(userRepository.getReferenceById(userId), group, MembershipRole.MEMBER));
        auditService.record(group.getId(), userId, AuditAction.MEMBER_JOINED, AuditEntityType.MEMBERSHIP,
                membership.getId());
        return GroupDetailResponse.of(group, MembershipRole.MEMBER, membershipRepository.countByGroupId(group.getId()));
    }

    private Membership requireMembership(Long groupId, Long userId) {
        return membershipRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new NotFoundException("Group not found"));
    }

    private String uniqueSlug(String name) {
        String base = slugGenerator.slugify(name);
        String candidate = base;
        int suffix = 2;
        while (groupRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String uniqueInviteCode() {
        String code;
        do {
            code = inviteCodeGenerator.generate();
        } while (groupRepository.existsByInviteCode(code));
        return code;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
