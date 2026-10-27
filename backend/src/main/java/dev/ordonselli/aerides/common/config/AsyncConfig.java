package dev.ordonselli.aerides.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async}, used to send emails and Discord messages off the request thread.
 * Spring Boot provides the thread pool ({@code spring.task.execution.*}).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
