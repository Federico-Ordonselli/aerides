package dev.ordonselli.raidboard.event;

/** An event went from DRAFT to PUBLISHED: members can now see it and sign up. */
public record EventPublishedEvent(Long groupId, Long eventId) {
}
