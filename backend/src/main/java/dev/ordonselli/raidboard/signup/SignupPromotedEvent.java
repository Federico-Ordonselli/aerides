package dev.ordonselli.raidboard.signup;

/** Published when a waitlisted user gets a seat. Milestone 4 turns it into an email and a WebSocket update. */
public record SignupPromotedEvent(Long groupId, Long eventId, Long signupId, Long userId) {
}
