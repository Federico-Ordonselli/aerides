package dev.ordonselli.raidboard.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import dev.ordonselli.raidboard.event.Event;
import dev.ordonselli.raidboard.event.EventRepository;
import dev.ordonselli.raidboard.group.Group;
import dev.ordonselli.raidboard.group.Membership;
import dev.ordonselli.raidboard.group.MembershipRepository;
import dev.ordonselli.raidboard.group.MembershipRole;
import dev.ordonselli.raidboard.user.User;

@ExtendWith(MockitoExtension.class)
class SubscriptionAuthorizerTest {

    private static final Long USER_ID = 5L;
    private static final Long GROUP_ID = 1L;
    private static final Long EVENT_ID = 10L;
    private static final String EVENT_TOPIC = "/topic/events/" + EVENT_ID;

    @Mock
    private EventRepository eventRepository;
    @Mock
    private MembershipRepository membershipRepository;

    private SubscriptionAuthorizer authorizer;
    private Group group;
    private Event event;

    @BeforeEach
    void setUp() {
        authorizer = new SubscriptionAuthorizer(eventRepository, membershipRepository);
        group = new Group("Gilda", "gilda", null, "ABCDEFGHJK");
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        event = new Event(group, null, "Raid", Instant.parse("2027-01-12T20:00:00Z"), 120);
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
    }

    @Test
    void everyoneMaySubscribeToTheirOwnNotificationQueue() {
        assertThat(authorizer.canSubscribe(USER_ID, "/user/queue/notifications")).isTrue();
    }

    @Test
    void aMemberMaySubscribeToAPublishedEvent() {
        event.publish();
        givenRole(MembershipRole.MEMBER);

        assertThat(authorizer.canSubscribe(USER_ID, EVENT_TOPIC)).isTrue();
    }

    @Test
    void someoneOutsideTheGroupMayNot() {
        event.publish();
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(membershipRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.empty());

        assertThat(authorizer.canSubscribe(USER_ID, EVENT_TOPIC)).isFalse();
    }

    @Test
    void draftsAreForOfficersOnly() {
        givenRole(MembershipRole.MEMBER);
        assertThat(authorizer.canSubscribe(USER_ID, EVENT_TOPIC)).isFalse();

        givenRole(MembershipRole.OFFICER);
        assertThat(authorizer.canSubscribe(USER_ID, EVENT_TOPIC)).isTrue();
    }

    @Test
    void anUnknownEventIsRefused() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThat(authorizer.canSubscribe(USER_ID, EVENT_TOPIC)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"/topic/events", "/topic/events/abc", "/topic/events/10/extra", "/topic/groups/1",
            "/queue/notifications-user123", "/user/queue/other"})
    void anyOtherDestinationIsRefused(String destination) {
        assertThat(authorizer.canSubscribe(USER_ID, destination)).isFalse();
    }

    private void givenRole(MembershipRole role) {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        User user = new User("mario@example.com", "hash", "Mario", "it");
        when(membershipRepository.findByGroupIdAndUserId(GROUP_ID, USER_ID))
                .thenReturn(Optional.of(new Membership(user, group, role)));
    }
}
