package dev.ordonselli.aerides.signup;

import java.time.Instant;

/** A sign-up as seen by the members of the group: display name only, never the email. */
public record SignupResponse(
        Long id,
        Long slotId,
        Long userId,
        String displayName,
        String avatarUrl,
        SignupStatus status,
        int position,
        Instant createdAt) {
}
