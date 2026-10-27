package dev.ordonselli.aerides.common.ratelimit;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Binding of {@code aerides.rate-limit.*}: each limit is "capacity requests every period". */
@Validated
@ConfigurationProperties(prefix = "aerides.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        @Valid @NotNull Limit loginPerIpAndEmail,
        @Valid @NotNull Limit loginPerIp,
        @Valid @NotNull Limit loginPerEmail,
        @Valid @NotNull Limit refreshPerIp) {

    public record Limit(@Positive int capacity, @NotNull Duration period) {
    }
}
