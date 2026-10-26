package dev.ordonselli.raidboard.realtime;

import dev.ordonselli.raidboard.event.EventChangeType;

/**
 * Sent on {@code /topic/events/{id}}. It only says that something changed: the event detail contains
 * fields that differ per user (their own sign-up, what they may do), so each client reloads it over
 * REST instead of receiving one shared copy.
 */
public record EventUpdateMessage(Long eventId, EventChangeType type) {
}
