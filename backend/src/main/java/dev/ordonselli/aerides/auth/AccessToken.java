package dev.ordonselli.aerides.auth;

import java.time.Instant;

/** A signed JWT and the moment it stops being valid. */
public record AccessToken(String value, Instant expiresAt) {
}
