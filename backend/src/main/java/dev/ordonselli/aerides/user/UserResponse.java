package dev.ordonselli.aerides.user;

import java.time.Instant;

/** Public view of a user. Never contains the password hash. */
public record UserResponse(
        Long id,
        String email,
        String displayName,
        String avatarUrl,
        String locale,
        Instant createdAt) {
}
