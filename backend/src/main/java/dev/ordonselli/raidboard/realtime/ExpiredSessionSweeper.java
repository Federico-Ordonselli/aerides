package dev.ordonselli.raidboard.realtime;

import java.time.Clock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes the WebSocket sessions whose access token has expired. A session opened with a stolen token,
 * or by a session revoked elsewhere, stops receiving messages at the latest one sweep after its token
 * expires (15 minutes at most).
 * <p>
 * The ERROR says "Authentication required", the same as a refused CONNECT: the frontend refreshes its
 * token on that message and reconnects, so a legitimate user just gets a new session.
 */
@Component
public class ExpiredSessionSweeper {

    private final TokenExpiryRegistry tokenExpiryRegistry;
    private final StompSessionCloser sessionCloser;
    private final Clock clock;

    public ExpiredSessionSweeper(TokenExpiryRegistry tokenExpiryRegistry, StompSessionCloser sessionCloser,
            Clock clock) {
        this.tokenExpiryRegistry = tokenExpiryRegistry;
        this.sessionCloser = sessionCloser;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${raidboard.realtime.expired-session-sweep-interval}")
    public void closeExpiredSessions() {
        for (String sessionId : tokenExpiryRegistry.expiredSessions(clock.instant())) {
            // Removed now, not only on disconnect, so a slow close isn't attempted again by the next sweep
            tokenExpiryRegistry.remove(sessionId);
            sessionCloser.close(sessionId, StompAuthInterceptor.AUTHENTICATION_REQUIRED);
        }
    }
}
