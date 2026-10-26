package dev.ordonselli.raidboard.group;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ordonselli.raidboard.audit.AuditAction;
import dev.ordonselli.raidboard.audit.AuditEntityType;
import dev.ordonselli.raidboard.audit.AuditService;
import dev.ordonselli.raidboard.common.error.BusinessRuleException;
import dev.ordonselli.raidboard.common.error.NotFoundException;

@Service
public class MembershipService {

    /** Owner first, then officers, then members; alphabetical inside each role. */
    private static final Comparator<Membership> MEMBER_ORDER = Comparator
            .comparing(Membership::getRole, Comparator.reverseOrder())
            .thenComparing(membership -> membership.getUser().getDisplayName(), String.CASE_INSENSITIVE_ORDER);

    private final MembershipRepository membershipRepository;
    private final MembershipMapper membershipMapper;
    private final AuditService auditService;

    public MembershipService(MembershipRepository membershipRepository, MembershipMapper membershipMapper,
            AuditService auditService) {
        this.membershipRepository = membershipRepository;
        this.membershipMapper = membershipMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long groupId) {
        return membershipRepository.findAllWithUserByGroupId(groupId).stream()
                .sorted(MEMBER_ORDER)
                .map(membershipMapper::toMemberResponse)
                .toList();
    }

    @Transactional
    public MemberResponse changeRole(Long groupId, Long actorId, Long targetUserId, MembershipRole newRole) {
        if (actorId.equals(targetUserId)) {
            throw new BusinessRuleException("You cannot change your own role");
        }
        if (newRole == MembershipRole.OWNER) {
            throw new BusinessRuleException("A group has exactly one owner: ownership transfer is not supported");
        }
        Membership target = requireMembership(groupId, targetUserId);
        MembershipRole previousRole = target.getRole();
        if (previousRole != newRole) {
            target.setRole(newRole);
            auditService.record(groupId, actorId, AuditAction.MEMBER_ROLE_CHANGED, AuditEntityType.MEMBERSHIP,
                    target.getId(), Map.of("userId", targetUserId, "from", previousRole, "to", newRole));
        }
        return membershipMapper.toMemberResponse(target);
    }

    @Transactional
    public void removeMember(Long groupId, Long actorId, Long targetUserId) {
        if (actorId.equals(targetUserId)) {
            throw new BusinessRuleException("You cannot remove yourself: delete the group instead");
        }
        Membership target = requireMembership(groupId, targetUserId);
        auditService.record(groupId, actorId, AuditAction.MEMBER_REMOVED, AuditEntityType.MEMBERSHIP, target.getId(),
                Map.of("userId", targetUserId));
        membershipRepository.delete(target);
    }

    @Transactional
    public void leave(Long groupId, Long userId) {
        Membership membership = requireMembership(groupId, userId);
        if (membership.getRole() == MembershipRole.OWNER) {
            throw new BusinessRuleException("The owner cannot leave the group: delete it instead");
        }
        auditService.record(groupId, userId, AuditAction.MEMBER_LEFT, AuditEntityType.MEMBERSHIP,
                membership.getId());
        membershipRepository.delete(membership);
    }

    private Membership requireMembership(Long groupId, Long userId) {
        return membershipRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new NotFoundException("Member not found"));
    }
}
