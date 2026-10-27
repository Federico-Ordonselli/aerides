package dev.ordonselli.aerides.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import dev.ordonselli.aerides.audit.AuditAction;
import dev.ordonselli.aerides.audit.AuditEntityType;
import dev.ordonselli.aerides.audit.AuditService;
import dev.ordonselli.aerides.common.error.ConflictException;
import dev.ordonselli.aerides.common.error.NotFoundException;
import dev.ordonselli.aerides.user.User;
import dev.ordonselli.aerides.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private InviteCodeGenerator inviteCodeGenerator;
    @Mock
    private AuditService auditService;

    private GroupService groupService;
    private User user;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(groupRepository, membershipRepository, userRepository, new SlugGenerator(),
                inviteCodeGenerator, auditService);
        user = new User("mario@example.com", "hash", "Mario", "it");
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void createMakesTheCreatorOwnerAndWritesAnAuditEntry() {
        when(inviteCodeGenerator.generate()).thenReturn("ABCDEFGHJK");
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));

        GroupDetailResponse response = groupService.create(1L, new CreateGroupRequest("  Gilda dei Draghi ", ""));

        ArgumentCaptor<Membership> membership = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository).save(membership.capture());
        assertThat(membership.getValue().getRole()).isEqualTo(MembershipRole.OWNER);
        assertThat(response.name()).isEqualTo("Gilda dei Draghi");
        assertThat(response.slug()).isEqualTo("gilda-dei-draghi");
        assertThat(response.description()).isNull();
        assertThat(response.inviteCode()).isEqualTo("ABCDEFGHJK");
        verify(auditService).record(eq(10L), eq(1L), eq(AuditAction.GROUP_CREATED), eq(AuditEntityType.GROUP),
                eq(10L), anyMap());
    }

    @Test
    void createAddsASuffixWhenTheSlugIsTaken() {
        when(groupRepository.existsBySlug("raid-night")).thenReturn(true);
        when(groupRepository.existsBySlug("raid-night-2")).thenReturn(true);
        when(inviteCodeGenerator.generate()).thenReturn("ABCDEFGHJK");
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));

        GroupDetailResponse response = groupService.create(1L, new CreateGroupRequest("Raid Night", null));

        assertThat(response.slug()).isEqualTo("raid-night-3");
    }

    @Test
    void joinWithUnknownCodeFails() {
        when(groupRepository.findByInviteCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.join(1L, new JoinGroupRequest(" nope ")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void joinTwiceFails() {
        Group group = withId(new Group("Raid", "raid", null, "ABCDEFGHJK"), 10L);
        when(groupRepository.findByInviteCode("ABCDEFGHJK")).thenReturn(Optional.of(group));
        when(membershipRepository.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> groupService.join(1L, new JoinGroupRequest("abcdefghjk")))
                .isInstanceOf(ConflictException.class);
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void discordWebhookUrlIsNeverWrittenToTheAuditLog() {
        Group group = withId(new Group("Raid", "raid", null, "ABCDEFGHJK"), 10L);
        when(membershipRepository.findByGroupIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(new Membership(user, group, MembershipRole.OWNER)));
        String secretUrl = "https://discord.com/api/webhooks/123/secret-token";

        groupService.updateDiscordWebhook(10L, 1L, new DiscordWebhookRequest(secretUrl));

        verify(auditService).record(10L, 1L, AuditAction.DISCORD_WEBHOOK_UPDATED, AuditEntityType.GROUP, 10L,
                Map.of("configured", true));
        assertThat(group.getDiscordWebhookUrl()).isEqualTo(secretUrl);
    }

    @Test
    void detailHidesInviteCodeFromMembersAndWebhookFromEveryoneButTheOwner() {
        Group group = withId(new Group("Raid", "raid", null, "ABCDEFGHJK"), 10L);
        group.setDiscordWebhookUrl("https://discord.com/api/webhooks/123/secret-token");

        GroupDetailResponse asMember = GroupDetailResponse.of(group, MembershipRole.MEMBER, 3);
        GroupDetailResponse asOfficer = GroupDetailResponse.of(group, MembershipRole.OFFICER, 3);
        GroupDetailResponse asOwner = GroupDetailResponse.of(group, MembershipRole.OWNER, 3);

        assertThat(asMember.inviteCode()).isNull();
        assertThat(asMember.discordWebhookUrl()).isNull();
        assertThat(asMember.hasDiscordWebhook()).isTrue();
        assertThat(asOfficer.inviteCode()).isEqualTo("ABCDEFGHJK");
        assertThat(asOfficer.discordWebhookUrl()).isNull();
        assertThat(asOwner.discordWebhookUrl()).isNotNull();
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
