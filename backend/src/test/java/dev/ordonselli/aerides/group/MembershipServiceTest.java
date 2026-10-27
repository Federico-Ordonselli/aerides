package dev.ordonselli.aerides.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import dev.ordonselli.aerides.audit.AuditAction;
import dev.ordonselli.aerides.audit.AuditEntityType;
import dev.ordonselli.aerides.audit.AuditService;
import dev.ordonselli.aerides.common.error.BusinessRuleException;
import dev.ordonselli.aerides.user.User;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    private static final Long GROUP_ID = 10L;
    private static final Long OWNER_ID = 1L;
    private static final Long MEMBER_ID = 2L;

    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MembershipService membershipService;
    private Group group;

    @BeforeEach
    void setUp() {
        // The real MapStruct implementation, generated at compile time
        membershipService = new MembershipService(membershipRepository, Mappers.getMapper(MembershipMapper.class),
                auditService, eventPublisher);
        group = new Group("Raid", "raid", null, "ABCDEFGHJK");
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
    }

    @Test
    void ownerCanPromoteAMemberToOfficer() {
        Membership member = membership(MEMBER_ID, MembershipRole.MEMBER);
        when(membershipRepository.findByGroupIdAndUserId(GROUP_ID, MEMBER_ID)).thenReturn(Optional.of(member));

        MemberResponse response = membershipService.changeRole(GROUP_ID, OWNER_ID, MEMBER_ID, MembershipRole.OFFICER);

        assertThat(response.role()).isEqualTo(MembershipRole.OFFICER);
        verify(auditService).record(GROUP_ID, OWNER_ID, AuditAction.MEMBER_ROLE_CHANGED, AuditEntityType.MEMBERSHIP,
                member.getId(), Map.of("userId", MEMBER_ID, "from", MembershipRole.MEMBER, "to", MembershipRole.OFFICER));
        verifyNoInteractions(eventPublisher); // gaining access never closes WebSocket sessions
    }

    @Test
    void demotingAnOfficerRevokesTheirAccessToDrafts() {
        when(membershipRepository.findByGroupIdAndUserId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(membership(MEMBER_ID, MembershipRole.OFFICER)));

        membershipService.changeRole(GROUP_ID, OWNER_ID, MEMBER_ID, MembershipRole.MEMBER);

        verify(eventPublisher).publishEvent(new GroupAccessRevokedEvent(GROUP_ID, MEMBER_ID));
        // Still a member: their sign-ups stay
        verify(eventPublisher, never()).publishEvent(new MembershipEndedEvent(GROUP_ID, MEMBER_ID));
    }

    @Test
    void removingAMemberEndsTheMembershipAndRevokesTheirAccess() {
        Membership member = membership(MEMBER_ID, MembershipRole.MEMBER);
        when(membershipRepository.findByGroupIdAndUserId(GROUP_ID, MEMBER_ID)).thenReturn(Optional.of(member));

        membershipService.removeMember(GROUP_ID, OWNER_ID, MEMBER_ID);

        verify(membershipRepository).delete(member);
        verify(eventPublisher).publishEvent(new MembershipEndedEvent(GROUP_ID, MEMBER_ID));
        verify(eventPublisher).publishEvent(new GroupAccessRevokedEvent(GROUP_ID, MEMBER_ID));
    }

    @Test
    void leavingEndsTheMembershipAndRevokesAccess() {
        when(membershipRepository.findByGroupIdAndUserId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(membership(MEMBER_ID, MembershipRole.MEMBER)));

        membershipService.leave(GROUP_ID, MEMBER_ID);

        verify(eventPublisher).publishEvent(new MembershipEndedEvent(GROUP_ID, MEMBER_ID));
        verify(eventPublisher).publishEvent(new GroupAccessRevokedEvent(GROUP_ID, MEMBER_ID));
    }

    @Test
    void nobodyCanChangeTheirOwnRole() {
        assertThatThrownBy(() -> membershipService.changeRole(GROUP_ID, OWNER_ID, OWNER_ID, MembershipRole.MEMBER))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(auditService);
    }

    @Test
    void ownershipCannotBeAssigned() {
        assertThatThrownBy(() -> membershipService.changeRole(GROUP_ID, OWNER_ID, MEMBER_ID, MembershipRole.OWNER))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void settingTheSameRoleWritesNoAuditEntry() {
        when(membershipRepository.findByGroupIdAndUserId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(membership(MEMBER_ID, MembershipRole.MEMBER)));

        membershipService.changeRole(GROUP_ID, OWNER_ID, MEMBER_ID, MembershipRole.MEMBER);

        verifyNoInteractions(auditService);
    }

    @Test
    void theOwnerCannotLeave() {
        when(membershipRepository.findByGroupIdAndUserId(GROUP_ID, OWNER_ID))
                .thenReturn(Optional.of(membership(OWNER_ID, MembershipRole.OWNER)));

        assertThatThrownBy(() -> membershipService.leave(GROUP_ID, OWNER_ID))
                .isInstanceOf(BusinessRuleException.class);
        verify(membershipRepository, never()).delete(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void theOwnerCannotRemoveThemselves() {
        assertThatThrownBy(() -> membershipService.removeMember(GROUP_ID, OWNER_ID, OWNER_ID))
                .isInstanceOf(BusinessRuleException.class);
        verify(membershipRepository, never()).findByGroupIdAndUserId(anyLong(), anyLong());
    }

    private Membership membership(Long userId, MembershipRole role) {
        User user = new User("user" + userId + "@example.com", "hash", "User " + userId, "it");
        ReflectionTestUtils.setField(user, "id", userId);
        Membership membership = new Membership(user, group, role);
        ReflectionTestUtils.setField(membership, "id", 100L + userId);
        return membership;
    }
}
