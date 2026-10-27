package dev.ordonselli.raidboard.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class StompAuthInterceptorTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final String SESSION_ID = "session-1";

    @Mock
    private JwtDecoder jwtDecoder;
    @Mock
    private SubscriptionAuthorizer subscriptionAuthorizer;

    private final MessageChannel channel = mock(MessageChannel.class);
    private final TokenExpiryRegistry tokenExpiryRegistry = new TokenExpiryRegistry();
    private StompAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthInterceptor(jwtDecoder, subscriptionAuthorizer, tokenExpiryRegistry,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void connectWithAValidTokenAttachesTheUserToTheSession() {
        Instant expiresAt = NOW.plusSeconds(60);
        when(jwtDecoder.decode("good")).thenReturn(jwt("5", expiresAt));
        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setSessionId(SESSION_ID);
        connect.addNativeHeader("Authorization", "Bearer good");

        Message<?> result = interceptor.preSend(message(connect), channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(accessor.getUser().getName()).isEqualTo("5");
        // The token's expiry is remembered for the later frames and for the sweep
        assertThat(tokenExpiryRegistry.isExpired(SESSION_ID, expiresAt.minusSeconds(1))).isFalse();
        assertThat(tokenExpiryRegistry.isExpired(SESSION_ID, expiresAt)).isTrue();
    }

    @Test
    void connectWithoutAuthorizationHeaderIsRejected() {
        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);

        assertThatThrownBy(() -> interceptor.preSend(message(connect), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connectWithAnInvalidTokenIsRejectedWithoutSayingWhy() {
        when(jwtDecoder.decode("expired")).thenThrow(new BadJwtException("Jwt expired at 2026-01-01"));
        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.addNativeHeader("Authorization", "Bearer expired");

        assertThatThrownBy(() -> interceptor.preSend(message(connect), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage(StompAuthInterceptor.AUTHENTICATION_REQUIRED);
    }

    @Test
    void subscribeWithoutAnAuthenticatedSessionIsRejected() {
        StompHeaderAccessor subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setDestination("/user/queue/notifications");

        assertThatThrownBy(() -> interceptor.preSend(message(subscribe), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verify(subscriptionAuthorizer, never()).canSubscribe(anyLong(), any());
    }

    @Test
    void subscribeIsCheckedForTheSessionUser() {
        tokenExpiryRegistry.register(SESSION_ID, NOW.plusSeconds(60));
        StompHeaderAccessor subscribe = subscribe("/topic/events/10");
        when(subscriptionAuthorizer.canSubscribe(5L, "/topic/events/10")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message(subscribe), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("Subscription not allowed");
    }

    @Test
    void subscribeAfterTheTokenExpiredIsRejected() {
        tokenExpiryRegistry.register(SESSION_ID, NOW);

        assertThatThrownBy(() -> interceptor.preSend(message(subscribe("/topic/events/10")), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage(StompAuthInterceptor.AUTHENTICATION_REQUIRED);
        verify(subscriptionAuthorizer, never()).canSubscribe(anyLong(), any());
    }

    @Test
    void clientsCannotSendMessages() {
        StompHeaderAccessor send = StompHeaderAccessor.create(StompCommand.SEND);
        send.setDestination("/topic/events/10");
        send.setUser(() -> "5");

        assertThatThrownBy(() -> interceptor.preSend(message(send), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    private static StompHeaderAccessor subscribe(String destination) {
        StompHeaderAccessor subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setDestination(destination);
        subscribe.setSessionId(SESSION_ID);
        subscribe.setUser(() -> "5");
        return subscribe;
    }

    private static Jwt jwt(String subject, Instant expiresAt) {
        return Jwt.withTokenValue("good").header("alg", "HS256").subject(subject)
                .issuedAt(expiresAt.minusSeconds(900)).expiresAt(expiresAt).build();
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
