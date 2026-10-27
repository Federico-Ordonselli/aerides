package dev.ordonselli.aerides.notification.discord;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aerides.discord")
public record DiscordProperties(@NotNull Duration connectTimeout, @NotNull Duration readTimeout) {
}
