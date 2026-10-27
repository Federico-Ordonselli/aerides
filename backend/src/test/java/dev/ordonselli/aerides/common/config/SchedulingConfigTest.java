package dev.ordonselli.aerides.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import dev.ordonselli.aerides.realtime.ExpiredSessionSweeper;
import dev.ordonselli.aerides.realtime.StompSessionCloser;
import dev.ordonselli.aerides.realtime.TokenExpiryRegistry;

/**
 * A real scheduled job next to a scheduler like the one the WebSocket broker registers for its heartbeats.
 * Being the only TaskScheduler in the context, that one used to run every {@code @Scheduled} job too.
 */
class SchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class)
            .withPropertyValues("aerides.realtime.expired-session-sweep-interval=1h")
            .withBean("messageBrokerTaskScheduler", ThreadPoolTaskScheduler.class, () -> {
                ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
                scheduler.setThreadNamePrefix("MessageBroker-");
                return scheduler;
            })
            .withBean(StompSessionCloser.class, () -> mock(StompSessionCloser.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(ExpiredSessionSweeper.class);

    @Test
    void scheduledJobsRunOnTheirOwnSchedulerNotOnTheBrokers() {
        // Stubbed before the context starts: the first run of a fixed-delay job begins right away
        CompletableFuture<String> thread = new CompletableFuture<>();
        TokenExpiryRegistry registry = mock(TokenExpiryRegistry.class);
        when(registry.expiredSessions(any())).thenAnswer(invocation -> {
            thread.complete(Thread.currentThread().getName());
            return List.of();
        });

        contextRunner.withBean(TokenExpiryRegistry.class, () -> registry).run(context ->
                // The timeout only stops a broken test from hanging
                assertThat(thread.get(5, TimeUnit.SECONDS)).startsWith(SchedulingConfig.THREAD_NAME_PREFIX));
    }

    @Test
    void schedulingCanBeTurnedOff() {
        contextRunner.withPropertyValues("aerides.scheduling.enabled=false")
                .withBean(TokenExpiryRegistry.class, () -> mock(TokenExpiryRegistry.class))
                .run(context -> assertThat(context).doesNotHaveBean(SchedulingConfig.JOB_SCHEDULER_BEAN));
    }
}
