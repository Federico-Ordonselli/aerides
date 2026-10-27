package dev.ordonselli.aerides.common.config;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The thread pool of {@code @Async} methods, see {@link AsyncConfig}.
 *
 * @param coreSize         threads kept while there is work (they stop after a minute without any)
 * @param maxSize          upper limit: threads beyond {@code coreSize} are added only once the queue is full
 * @param queueCapacity    tasks waiting for a free thread
 * @param awaitTermination how long the shutdown waits for queued and running tasks
 */
@Validated
@ConfigurationProperties(prefix = "aerides.async")
public record AsyncProperties(
        @Min(1) int coreSize,
        @Min(1) int maxSize,
        @Min(0) int queueCapacity,
        @NotNull Duration awaitTermination) {
}
