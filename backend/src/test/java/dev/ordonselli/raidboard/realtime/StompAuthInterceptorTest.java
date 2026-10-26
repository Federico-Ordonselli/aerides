package dev.ordonselli.raidboard.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

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

    @Mock
    private JwtDecoder jwtDecoder;
    @Mock
    private SubscriptionAuthorizer subscriptionAuthorizer;

    private final MessageChannel channel = mock(MessageChannel.class);
    private StompAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthInterceptor(jwtDecoder, subscriptionAuthorizer);
    }

    @Test
    void connectWithAValidTokenAttachesTheUserToTheSession() {
        Jwt jwt = Jwt.withTokenValue("good").header("alg", "HS256").subject("5")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(jwtDecoder.decode("good")).thenReturn(jwt);
        StompHeaderAccessor connect = StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.addNativeHeader("Authorization", "Bearer good");

        Message<?> result = interceptor.preSend(message(connect), channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(accessor.getUser().getName()).isEqualTo("5");
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
                .hasMessage("Authentication required");
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
        StompHeaderAccessor subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setDestination("/topic/events/10");
        subscribe.setUser(() -> "5");
        when(subscriptionAuthorizer.canSubscribe(5L, "/topic/events/10")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message(subscribe), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessage("Subscription not allowed");
    }

    @Test
    void clientsCannotSendMessages() {
        StompHeaderAccessor send = StompHeaderAccessor.create(StompCommand.SEND);
        send.setDestination("/topic/events/10");
        send.setUser(() -> "5");

        assertThatThrownBy(() -> interceptor.preSend(message(send), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
