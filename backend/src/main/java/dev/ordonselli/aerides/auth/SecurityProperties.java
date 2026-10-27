package dev.ordonselli.aerides.auth;

import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe binding of the {@code aerides.security.*} properties.
 * {@link Validated} makes the application fail at startup if a value is missing. Except jwtSecret: it's checked
 * where the signing key is built ({@link SecurityConfig#signingKey}), because a failed validation here would print
 * the secret in the startup log.
 */
@Validated
@ConfigurationProperties(prefix = "aerides.security")
public record SecurityProperties(
        String jwtSecret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        boolean refreshCookieSecure,
        @NotEmpty List<String> allowedOrigins) {
}
