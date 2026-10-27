package dev.ordonselli.aerides.common.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async}, used to send emails and Discord messages off the request thread, and gives it a
 * bounded pool of its own.
 * <p>
 * Spring Boot's executor is not used: Boot creates it only when no other executor exists, and the WebSocket
 * broker registers three. Spring would then not know which one to pick and start a new thread for every task.
 * Implementing {@link AsyncConfigurer} names the executor explicitly, so a new {@code @Async} method can't end up
 * elsewhere by accident.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(AsyncProperties.class)
public class AsyncConfig implements AsyncConfigurer {

    public static final String EXECUTOR_BEAN = "notificationExecutor";
    public static final String THREAD_NAME_PREFIX = "notify-";

    private final AsyncProperties properties;

    public AsyncConfig(AsyncProperties properties) {
        this.properties = properties;
    }

    @Bean(EXECUTOR_BEAN)
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setCorePoolSize(properties.coreSize());
        executor.setMaxPoolSize(properties.maxSize());
        executor.setQueueCapacity(properties.queueCapacity());
        // Idle threads go away, so a quiet instance holds no memory for them
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        // When the pool and the queue are full, the thread that submits the task runs it: slower, but nothing is
        // lost. Rejecting instead would throw in afterCommit(), turning an already committed request into a 500.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // On shutdown, queued and running tasks finish (up to awaitTermination) instead of being interrupted
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(properties.awaitTermination().toMillis());
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return notificationExecutor();
    }
}
