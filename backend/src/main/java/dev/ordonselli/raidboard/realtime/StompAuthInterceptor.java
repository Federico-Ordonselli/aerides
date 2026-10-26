package dev.ordonselli.raidboard.realtime;

import java.security.Principal;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * Authentication and authorization for STOMP frames.
 * <p>
 * The HTTP security chain only sees the WebSocket handshake, and browsers can't add an
 * Authorization header to it. So the handshake is public, and the checks happen here, on the frames:
 * <ul>
 *   <li>CONNECT must carry a valid access token ({@code Authorization: Bearer ...}). The resulting
 *       user is attached to the session and comes with every later frame.</li>
 *   <li>SUBSCRIBE is allowed only to destinations the user may read (see {@link SubscriptionAuthorizer}).</li>
 *   <li>SEND is never allowed: clients only listen, every change goes through the REST API.</li>
 * </ul>
 * Throwing here makes Spring answer with a STOMP ERROR frame and close the connection.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
    private final SubscriptionAuthorizer subscriptionAuthorizer;

    public StompAuthInterceptor(JwtDecoder jwtDecoder, SubscriptionAuthorizer subscriptionAuthorizer) {
        this.jwtDecoder = jwtDecoder;
        this.subscriptionAuthorizer = subscriptionAuthorizer;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // heartbeats
        }
        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT, STOMP -> accessor.setUser(authenticate(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)));
            case SUBSCRIBE -> {
                Principal user = requireUser(accessor);
                if (!subscriptionAuthorizer.canSubscribe(Long.valueOf(user.getName()), accessor.getDestination())) {
                    throw new MessageDeliveryException("Subscription not allowed");
                }
            }
            case UNSUBSCRIBE, DISCONNECT -> {
                // always fine
            }
            default -> throw new MessageDeliveryException(command + " is not supported");
        }
        return message;
    }

    /** Same validation as the REST API: signature, expiry and issuer. The error never says what was wrong. */
    private Principal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException("Authentication required");
        }
        try {
            return authenticationConverter.convert(jwtDecoder.decode(authorization.substring(BEARER_PREFIX.length())));
        } catch (JwtException e) {
            throw new MessageDeliveryException("Authentication required");
        }
    }

    private static Principal requireUser(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new MessageDeliveryException("Authentication required");
        }
        return user;
    }
}
