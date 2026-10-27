package dev.ordonselli.raidboard.realtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * When the access token of each open WebSocket session expires.
 * <p>
 * The token is checked once, on CONNECT. This registry keeps its {@code exp}, so later frames can be
 * refused once it has passed and {@link ExpiredSessionSweeper} can close the sessions that outlived it.
 * Spring's {@code SimpUserRegistry} doesn't expose session attributes, hence a map of its own, keyed by
 * session id. It is in memory, like the broker: one backend instance (see {@link WebSocketConfig}).
 */
@Component
public class TokenExpiryRegistry {

    private final Map<String, Instant> expiresAtBySession = new ConcurrentHashMap<>();

    public void register(String sessionId, Instant expiresAt) {
        expiresAtBySession.put(sessionId, expiresAt);
    }

    /** A session that never registered a token counts as expired: when in doubt, refuse. */
    public boolean isExpired(String sessionId, Instant now) {
        Instant expiresAt = sessionId == null ? null : expiresAtBySession.get(sessionId);
        return expiresAt == null || !now.isBefore(expiresAt);
    }

    public List<String> expiredSessions(Instant now) {
        return expiresAtBySession.entrySet().stream()
                .filter(entry -> !now.isBefore(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    public void remove(String sessionId) {
        expiresAtBySession.remove(sessionId);
    }

    /** Every closed session, whoever closed it (client, server, network), leaves the map. */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        remove(event.getSessionId());
    }
}
