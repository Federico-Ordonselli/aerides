package dev.ordonselli.raidboard.event;

/**
 * Lifecycle of an event: DRAFT → PUBLISHED → COMPLETED, and DRAFT or PUBLISHED → CANCELLED.
 * Only PUBLISHED events accept sign-ups; members never see DRAFT events.
 */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED,
    COMPLETED
}
