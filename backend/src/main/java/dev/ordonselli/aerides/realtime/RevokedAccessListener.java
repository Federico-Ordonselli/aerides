package dev.ordonselli.aerides.realtime;

import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.ordonselli.aerides.group.GroupAccessRevokedEvent;

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

    private final SimpUserRegistry userRegistry;
    private final StompSessionCloser sessionCloser;

    public RevokedAccessListener(SimpUserRegistry userRegistry, StompSessionCloser sessionCloser) {
        this.userRegistry = userRegistry;
        this.sessionCloser = sessionCloser;
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
            sessionCloser.close(session.getId(), ACCESS_REVOKED);
        }
    }
}
