package dev.ordonselli.aerides.event;

/** A sign-up left exactly one free seat in the whole event (business rule 9). */
public record EventAlmostFullEvent(Long groupId, Long eventId) {
}
