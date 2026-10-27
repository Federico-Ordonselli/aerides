package dev.ordonselli.aerides.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import dev.ordonselli.aerides.event.EventAlmostFullEvent;
import dev.ordonselli.aerides.event.EventPublishedEvent;
import dev.ordonselli.aerides.event.EventRepository;
import dev.ordonselli.aerides.notification.NotificationService;
import dev.ordonselli.aerides.notification.PromotionEmailListener;
import dev.ordonselli.aerides.notification.discord.DiscordNotifier;
import dev.ordonselli.aerides.notification.discord.DiscordWebhookClient;
import dev.ordonselli.aerides.signup.SignupPromotedEvent;
import dev.ordonselli.aerides.signup.SignupRepository;

/**
 * The real async listeners in a small context, next to two more executors like the ones the WebSocket broker
 * registers. With several executors and none named "taskExecutor", Spring used to run {@code @Async} methods on a
 * new thread per task ({@code SimpleAsyncTaskExecutor}). The tests check which thread the listeners run on, never
 * how long anything takes.
 */
class AsyncConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AsyncConfig.class)
            .withPropertyValues("aerides.async.core-size=2", "aerides.async.max-size=4",
                    "aerides.async.queue-capacity=100", "aerides.async.await-termination=20s",
                    // Bound even though the beans are mocks: their classes enable these properties
                    "aerides.notifications.app-url=http://localhost", "aerides.notifications.mail-from=test@localhost",
                    "aerides.notifications.time-zone=UTC", "aerides.notifications.reminder-lead-time=24h",
                    "aerides.notifications.max-attempts=3",
                    "aerides.discord.connect-timeout=2s", "aerides.discord.read-timeout=5s")
            .withBean("clientInboundChannelExecutor", ThreadPoolTaskExecutor.class, ThreadPoolTaskExecutor::new)
            .withBean("brokerChannelExecutor", ThreadPoolTaskExecutor.class, ThreadPoolTaskExecutor::new)
            .withBean(EventRepository.class, () -> mock(EventRepository.class))
            .withBean(DiscordWebhookClient.class, () -> mock(DiscordWebhookClient.class))
            .withBean("messageSource", MessageSource.class, () -> mock(MessageSource.class))
            .withBean(SignupRepository.class, () -> mock(SignupRepository.class))
            .withBean(NotificationService.class, () -> mock(NotificationService.class))
            .withBean(DiscordNotifier.class)
            .withBean(PromotionEmailListener.class);

    @Test
    void discordAnnouncementsRunOnTheNotificationPool() {
        contextRunner.run(context -> {
            CompletableFuture<String> published = new CompletableFuture<>();
            CompletableFuture<String> almostFull = new CompletableFuture<>();
            EventRepository eventRepository = context.getBean(EventRepository.class);
            when(eventRepository.findWithGroupById(1L)).thenAnswer(invocation -> recordThread(published));
            when(eventRepository.findWithGroupById(2L)).thenAnswer(invocation -> recordThread(almostFull));

            DiscordNotifier notifier = context.getBean(DiscordNotifier.class);
            notifier.onPublished(new EventPublishedEvent(10L, 1L));
            notifier.onAlmostFull(new EventAlmostFullEvent(10L, 2L));

            // The timeout only stops a broken test from hanging
            assertThat(published.get(5, TimeUnit.SECONDS)).startsWith(AsyncConfig.THREAD_NAME_PREFIX);
            assertThat(almostFull.get(5, TimeUnit.SECONDS)).startsWith(AsyncConfig.THREAD_NAME_PREFIX);
        });
    }

    @Test
    void promotionEmailsRunOnTheNotificationPool() {
        contextRunner.run(context -> {
            CompletableFuture<String> thread = new CompletableFuture<>();
            when(context.getBean(SignupRepository.class).findWithUserAndEventById(3L))
                    .thenAnswer(invocation -> recordThread(thread));

            context.getBean(PromotionEmailListener.class)
                    .onPromoted(new SignupPromotedEvent(10L, 1L, "Raid", 3L, 4L));

            assertThat(thread.get(5, TimeUnit.SECONDS)).startsWith(AsyncConfig.THREAD_NAME_PREFIX);
        });
    }

    @Test
    void thePoolIsBoundedByTheProperties() {
        contextRunner.run(context -> {
            ThreadPoolTaskExecutor executor = context.getBean(AsyncConfig.EXECUTOR_BEAN, ThreadPoolTaskExecutor.class);

            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
            assertThat(executor.getQueueCapacity()).isEqualTo(100);
        });
    }

    @Test
    void whenThePoolAndTheQueueAreFullTheCallerRunsTheTask() {
        contextRunner.withPropertyValues("aerides.async.core-size=1", "aerides.async.max-size=1",
                "aerides.async.queue-capacity=1").run(context -> {
            ThreadPoolTaskExecutor executor = context.getBean(AsyncConfig.EXECUTOR_BEAN, ThreadPoolTaskExecutor.class);
            CountDownLatch release = new CountDownLatch(1);
            try {
                executor.execute(() -> awaitQuietly(release)); // takes the only thread
                executor.execute(() -> { }); // takes the only queue place
                CompletableFuture<String> third = new CompletableFuture<>();

                executor.execute(() -> third.complete(Thread.currentThread().getName()));

                // Ran synchronously, on the thread that submitted it: nothing is dropped
                assertThat(third).isCompletedWithValue(Thread.currentThread().getName());
            } finally {
                release.countDown();
            }
        });
    }

    @Test
    void shutdownLetsRunningTasksFinishInsteadOfInterruptingThem() {
        contextRunner.run(context -> {
            ThreadPoolTaskExecutor executor = context.getBean(AsyncConfig.EXECUTOR_BEAN, ThreadPoolTaskExecutor.class);
            ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicBoolean interrupted = new AtomicBoolean();
            AtomicBoolean finished = new AtomicBoolean();
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await();
                    finished.set(true);
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
            });
            started.await(5, TimeUnit.SECONDS);

            Thread closing = Thread.ofPlatform().start(context::close);
            // Released only once the shutdown has begun, so the task is still running when it starts
            await().until(pool::isShutdown);
            release.countDown();
            closing.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(interrupted).isFalse();
            assertThat(finished).isTrue();
        });
    }

    private static <T> Optional<T> recordThread(CompletableFuture<String> thread) {
        thread.complete(Thread.currentThread().getName());
        return Optional.empty();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
