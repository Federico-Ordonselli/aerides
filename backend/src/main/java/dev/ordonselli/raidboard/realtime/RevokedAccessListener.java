package dev.ordonselli.raidboard.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.ordonselli.raidboard.group.GroupAccessRevokedEvent;

/**
 * Subscriptions are authorized once, on SUBSCRIBE. When a user loses access to a group, this closes
 * all their open WebSocket sessions, so nothing authorized with the old role keeps flowing.
 * <p>
 * Closing the session is simpler and safer than removing single subscriptions: the broker drops every
 * subscription of a closed session, the client reconnects with a fresh CONNECT, and each subscription
 * goes through {@link SubscriptionAuthorizer} again with the current role. A user removed from one group
 * is briefly reconnected in the others too, which costs nothing visible.
 */
@Component
public class RevokedAccessListener {

    /** Sent in the ERROR frame, so the client knows why it was disconnected. */
    public static final String ACCESS_REVOKED = "Access revoked";

    private static final Logger log = LoggerFactory.getLogger(RevokedAccessListener.class);

    private final SimpUserRegistry userRegistry;
    private final MessageChannel clientOutboundChannel;

    public RevokedAccessListener(SimpUserRegistry userRegistry,
            @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel) {
        this.userRegistry = userRegistry;
        this.clientOutboundChannel = clientOutboundChannel;
    }

    /** After the commit: if the removal is rolled back, the user keeps their sessions. */
    @TransactionalEventListener
    public void onAccessRevoked(GroupAccessRevokedEvent event) {
        // The principal name of a WebSocket session is the user id (the JWT subject), see StompAuthInterceptor
        SimpUser user = userRegistry.getUser(event.userId().toString());
        if (user == null) {
            return; // not connected
        }
        for (SimpSession session : user.getSessions()) {
            close(session.getId());
        }
    }

    /** Spring sends an ERROR frame to the client and then closes the WebSocket (StompSubProtocolHandler). */
    private void close(String sessionId) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.ERROR);
        headers.setMessage(ACCESS_REVOKED);
        headers.setSessionId(sessionId);
        try {
            clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));
        } catch (MessagingException e) {
            log.warn("WebSocket session {} not closed", sessionId, e);
        }
    }
}
