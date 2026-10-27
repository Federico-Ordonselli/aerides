package dev.ordonselli.aerides.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Enables {@code @Scheduled} jobs (reminder emails, email retries, closing expired WebSocket sessions) and gives
 * them a scheduler of their own. Integration tests turn it off and run the jobs by calling them, so a background
 * run never interferes with what a test checks.
 * <p>
 * Without it the jobs would run on the WebSocket broker's scheduler: Spring Boot doesn't create one when another
 * exists, and that one is the only scheduler left. It sends the STOMP heartbeats, so a reminder run waiting on
 * the mail server would delay them.
 */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty(name = "aerides.scheduling.enabled", matchIfMissing = true)
public class SchedulingConfig implements SchedulingConfigurer {

    public static final String JOB_SCHEDULER_BEAN = "jobScheduler";
    public static final String THREAD_NAME_PREFIX = "jobs-";

    /** Two threads: the session sweep, every minute, doesn't wait behind a reminder run that is sending emails. */
    @Bean(JOB_SCHEDULER_BEAN)
    public ThreadPoolTaskScheduler jobScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix(THREAD_NAME_PREFIX);
        scheduler.setPoolSize(2);
        // A run in progress (say, halfway through the reminders) finishes on shutdown instead of being interrupted
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(jobScheduler());
    }
}
