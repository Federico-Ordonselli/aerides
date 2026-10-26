package dev.ordonselli.raidboard.signup;

/**
 * Published when a waitlisted user gets a seat. After the commit it becomes an email to the user
 * and a live notification in their open browser tabs.
 */
public record SignupPromotedEvent(Long groupId, Long eventId, String eventTitle, Long signupId, Long userId) {
}
