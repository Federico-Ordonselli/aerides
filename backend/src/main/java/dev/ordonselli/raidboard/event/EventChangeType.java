package dev.ordonselli.raidboard.event;

/** What changed on an event. Clients use it to decide how to react (e.g. reload the sign-up list). */
public enum EventChangeType {
    /** Title, time, slots or other editable fields. */
    DETAILS,
    /** Published, cancelled or completed. */
    STATUS,
    /** Someone signed up, withdrew, or a sign-up was approved, rejected or promoted. */
    SIGNUPS
}
