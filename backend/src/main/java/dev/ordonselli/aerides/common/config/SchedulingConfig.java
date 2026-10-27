package dev.ordonselli.aerides.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} jobs (the reminder emails). Integration tests turn it off and run the
 * jobs by calling them, so a background run never interferes with what a test checks.
 */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty(name = "aerides.scheduling.enabled", matchIfMissing = true)
public class SchedulingConfig {
}
