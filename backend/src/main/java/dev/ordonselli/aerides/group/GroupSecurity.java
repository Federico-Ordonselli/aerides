package dev.ordonselli.aerides.group;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.ordonselli.aerides.common.error.NotFoundException;

/**
 * Per-group authorization checks, used from {@code @PreAuthorize} expressions:
 * <pre>{@code @PreAuthorize("@groupSecurity.hasRole(#groupId, 'OFFICER')")}</pre>
 * <p>
 * Someone who is not a member gets a 404 (NotFoundException), not a 403: answering "forbidden"
 * would confirm that the group exists. A member without the required role gets a 403
 * (the method returns false and Spring Security throws AccessDeniedException).
 */
@Component("groupSecurity")
public class GroupSecurity {

    private final MembershipRepository membershipRepository;

    public GroupSecurity(MembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long groupId) {
        return hasRole(groupId, MembershipRole.MEMBER.name());
    }

    @Transactional(readOnly = true)
    public boolean hasRole(Long groupId, String requiredRole) {
        MembershipRole role = membershipRepository.findByGroupIdAndUserId(groupId, currentUserId())
                .map(Membership::getRole)
                .orElseThrow(() -> new NotFoundException("Group not found"));
        return role.isAtLeast(MembershipRole.valueOf(requiredRole));
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return Long.valueOf(jwt.getSubject());
    }
}
