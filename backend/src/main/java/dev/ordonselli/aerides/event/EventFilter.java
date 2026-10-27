package dev.ordonselli.aerides.event;

import java.time.Instant;

/** Optional filters of the event list; a null field means "don't filter on it". {@code to} is exclusive. */
public record EventFilter(Instant from, Instant to, EventStatus status, Long gameId) {
}
