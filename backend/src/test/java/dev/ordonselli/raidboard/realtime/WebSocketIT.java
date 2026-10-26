package dev.ordonselli.raidboard.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import dev.ordonselli.raidboard.support.AbstractIntegrationTest;
import dev.ordonselli.raidboard.support.TestGroup;
import dev.ordonselli.raidboard.support.TestUser;

/**
 * Real STOMP clients against the running server: authentication on CONNECT, authorization on
 * SUBSCRIBE (business rule 10) and the messages sent after sign-up changes (business rule 3).
 */
class WebSocketIT extends AbstractIntegrationTest {

    private static final Instant NEXT_WEEK = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    private static final long TIMEOUT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private SimpleBrokerMessageHandler brokerMessageHandler;

    @Autowired
    private SimpUserRegistry userRegistry;

    private WebSocketStompClient stompClient;
    private TestUser owner;
    private TestUser member;
    private TestGroup group;

    /** ERROR frames received by the clients of the current test. */
    private final BlockingQueue<String> errors = new LinkedBlockingQueue<>();

    @BeforeEach
    void setUp() throws Exception {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        owner = newUser("Owner");
        member = newUser("Member");
        group = createGroup(owner, "Realtime guild");
        joinGroup(member, group);
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    @Test
    void connectWithoutATokenIsRejected() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(ExecutionException.class);
        assertThat(nextError()).isEqualTo("Authentication required");
    }

    @Test
    void connectWithAnInvalidTokenIsRejected() {
        assertThatThrownBy(() -> connect("not-a-jwt")).isInstanceOf(ExecutionException.class);
        assertThat(nextError()).isEqualTo("Authentication required");
    }

    @Test
    void aMemberReceivesAnUpdateWhenSomeoneSignsUp() throws Exception {
        String event = createPublishedEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 2));
        Long eventId = idOf(event);
        StompSession session = connect(member.accessToken());
        BlockingQueue<Map<String, Object>> updates = subscribe(session, "/topic/events/" + eventId);
        awaitBrokerSubscription("/topic/events/" + eventId);

        signUp(owner, group, eventId, slotIdOf(event, 0));

        assertThat(updates.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .containsEntry("eventId", eventId.intValue())
                .containsEntry("type", "SIGNUPS");
    }

    @Test
    void someoneOutsideTheGroupCannotSubscribeToItsEvents() throws Exception {
        Long eventId = idOf(createPublishedEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 2)));
        TestUser outsider = newUser("Outsider");
        StompSession session = connect(outsider.accessToken());

        subscribe(session, "/topic/events/" + eventId);

        assertThat(nextError()).isEqualTo("Subscription not allowed");
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS)).until(() -> !session.isConnected());
    }

    @Test
    void aMemberCannotSubscribeToADraft() throws Exception {
        Long eventId = idOf(createEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 2)));
        StompSession session = connect(member.accessToken());

        subscribe(session, "/topic/events/" + eventId);

        assertThat(nextError()).isEqualTo("Subscription not allowed");
    }

    @Test
    void theOnlyQueueAllowedIsYourOwnNotificationQueue() throws Exception {
        StompSession session = connect(member.accessToken());

        subscribe(session, "/queue/notifications-user" + owner.id());

        assertThat(nextError()).isEqualTo("Subscription not allowed");
    }

    @Test
    void aPromotedUserGetsAPersonalNotification() throws Exception {
        String event = createPublishedEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 1));
        Long eventId = idOf(event);
        signUp(owner, group, eventId, slotIdOf(event, 0));
        signUp(member, group, eventId, slotIdOf(event, 0)); // waitlisted
        StompSession session = connect(member.accessToken());
        BlockingQueue<Map<String, Object>> notifications = subscribe(session, "/user/queue/notifications");
        awaitBrokerSubscription("/queue/notifications-user" + sessionIdOf(member));

        mockMvc.perform(delete("/api/groups/{g}/events/{e}/signups/me", group.id(), eventId).with(as(owner)))
                .andExpect(status().isNoContent());

        assertThat(notifications.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .containsEntry("type", "SIGNUP_PROMOTED")
                .containsEntry("eventId", eventId.intValue())
                .containsEntry("eventTitle", "Raid night");
    }

    @Test
    void aRemovedMemberIsDisconnectedAndLosesTheirSubscriptions() throws Exception {
        Long eventId = idOf(createPublishedEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 2)));
        String topic = "/topic/events/" + eventId;
        StompSession ownerSession = connect(owner.accessToken());
        StompSession memberSession = connect(member.accessToken());
        subscribe(ownerSession, topic);
        subscribe(memberSession, topic);
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS)).until(() -> brokerSubscriptionCount(topic) == 2);

        mockMvc.perform(delete("/api/groups/{g}/members/{u}", group.id(), member.id()).with(as(owner)))
                .andExpect(status().isNoContent());

        assertThat(nextError()).isEqualTo(RevokedAccessListener.ACCESS_REVOKED);
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS)).until(() -> !memberSession.isConnected());
        // Only the member's subscription is gone: the owner keeps listening
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS)).until(() -> brokerSubscriptionCount(topic) == 1);
        assertThat(ownerSession.isConnected()).isTrue();
    }

    @Test
    void aMemberWhoLeavesIsDisconnected() throws Exception {
        StompSession session = connect(member.accessToken());

        mockMvc.perform(delete("/api/groups/{g}/members/me", group.id()).with(as(member)))
                .andExpect(status().isNoContent());

        assertThat(nextError()).isEqualTo(RevokedAccessListener.ACCESS_REVOKED);
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS)).until(() -> !session.isConnected());
    }

    @Test
    void anOfficerDemotedToMemberLosesTheirDraftSubscription() throws Exception {
        changeRole(owner, group, member, "OFFICER");
        String draftTopic = "/topic/events/" + idOf(createEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 2)));
        StompSession session = connect(member.accessToken());
        subscribe(session, draftTopic);
        awaitBrokerSubscription(draftTopic);

        changeRole(owner, group, member, "MEMBER");

        assertThat(nextError()).isEqualTo(RevokedAccessListener.ACCESS_REVOKED);
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS)).until(() -> brokerSubscriptionCount(draftTopic) == 0);
    }

    private StompSession connect(String accessToken) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (accessToken != null) {
            connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {
                            @Override
                            public void handleFrame(StompHeaders headers, Object payload) {
                                errors.add(headers.getFirst("message"));
                            }
                        })
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });
        return received;
    }

    private String nextError() {
        try {
            return errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * SUBSCRIBE is asynchronous: the broker registers it on another thread. Waiting until the broker
     * really has the subscription avoids publishing before anyone listens.
     */
    private void awaitBrokerSubscription(String brokerDestination) {
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS)).until(() -> brokerSubscriptionCount(brokerDestination) > 0);
    }

    /** How many subscriptions the broker would deliver a message on this destination to. */
    private int brokerSubscriptionCount(String brokerDestination) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination(brokerDestination);
        var probe = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        return brokerMessageHandler.getSubscriptionRegistry().findSubscriptions(probe).values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /** User queues are rewritten per session: /user/queue/x becomes /queue/x-user{sessionId}. */
    private String sessionIdOf(TestUser user) {
        await().atMost(Duration.ofSeconds(TIMEOUT_SECONDS))
                .until(() -> userRegistry.getUser(user.id().toString()) != null);
        return userRegistry.getUser(user.id().toString()).getSessions().iterator().next().getId();
    }
}
