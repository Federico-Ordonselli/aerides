package dev.ordonselli.aerides.game;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code aerides.rawg.*}. The API key is optional: without it game search answers 503. */
@Validated
@ConfigurationProperties(prefix = "aerides.rawg")
public record RawgProperties(
        @NotBlank String baseUrl,
        String apiKey,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {
}
