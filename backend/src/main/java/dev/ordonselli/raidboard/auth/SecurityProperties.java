package dev.ordonselli.raidboard.auth;

import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe binding of the {@code raidboard.security.*} properties.
 * {@link Validated} makes the application fail at startup if, for example, JWT_SECRET is missing or too short.
 */
@Validated
@ConfigurationProperties(prefix = "raidboard.security")
public record SecurityProperties(
        // HS256 needs a key of at least 256 bits = 32 bytes
        @NotBlank @Size(min = 32) String jwtSecret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        boolean refreshCookieSecure,
        @NotEmpty List<String> allowedOrigins) {
}
