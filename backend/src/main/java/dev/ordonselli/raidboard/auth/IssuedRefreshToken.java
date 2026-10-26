package dev.ordonselli.raidboard.auth;

import java.time.Instant;

import dev.ordonselli.raidboard.user.User;

/**
 * Result of issuing a refresh token: the raw value goes to the client (once), only its hash is stored.
 */
public record IssuedRefreshToken(String rawValue, Instant expiresAt, User user) {
}
