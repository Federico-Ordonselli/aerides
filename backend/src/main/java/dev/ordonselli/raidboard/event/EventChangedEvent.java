package dev.ordonselli.raidboard.event;

/**
 * Published inside the transaction that changes an event or its sign-ups. Listeners that talk to
 * the outside world (WebSocket) react only after the commit.
 */
public record EventChangedEvent(Long groupId, Long eventId, EventChangeType type) {
}
