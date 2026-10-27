package dev.ordonselli.aerides.event;

/** A number per event, e.g. confirmed sign-ups or total seats, computed by one GROUP BY query for a whole page. */
public record EventCount(Long eventId, long count) {
}
