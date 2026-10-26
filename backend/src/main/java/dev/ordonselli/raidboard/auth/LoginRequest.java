package dev.ordonselli.raidboard.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Upper bounds keep request values small: the email ends up in a rate-limit key held in memory,
 * and there's no point hashing a password longer than any valid one.
 */
public record LoginRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 128) String password) {
}
