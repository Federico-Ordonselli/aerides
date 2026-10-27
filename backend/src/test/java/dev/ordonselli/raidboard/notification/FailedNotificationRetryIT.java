package dev.ordonselli.raidboard.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.ordonselli.raidboard.support.AbstractIntegrationTest;
import dev.ordonselli.raidboard.support.TestGroup;
import dev.ordonselli.raidboard.support.TestUser;

/**
 * Retries of failed emails against PostgreSQL and GreenMail.
 * <p>
 * Instead of taking the mail server down, each test sends a reminder normally and then marks it FAILED
 * with SQL, as if the mail server had refused it: the retry only looks at status and attempts.
 */
class FailedNotificationRetryIT extends AbstractIntegrationTest {

    @Autowired
    private ReminderScheduler reminderScheduler;

    @Autowired
    private FailedNotificationRetrier retrier;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private TestUser owner;
    private TestUser member;
    private TestGroup group;
    private Long eventId;

    @BeforeEach
    void setUp() throws Exception {
        owner = newUser("Owner");
        member = newUser("Member");
        group = createGroup(owner, "Retry guild");
        joinGroup(member, group);
        String event = createPublishedEvent(owner, group,
                eventJson(Instant.now().plus(2, ChronoUnit.HOURS), false, 0, "Tank", 1));
        eventId = idOf(event);
        signUp(member, group, eventId, slotIdOf(event, 0));
        reminderScheduler.sendReminders();
        assertThat(emailsTo(member)).hasSize(1);
    }

    @Test
    void aFailedReminderIsSentAgain() {
        markReminderFailed(1);

        retrier.retryFailed();

        assertThat(emailsTo(member)).hasSize(2);
        assertThat(reminder())
                .returns(NotificationStatus.SENT, Notification::getStatus)
                .returns(2, Notification::getAttempts);
    }

    @Test
    void aReminderWithoutAttemptsLeftIsNotSentAgain() {
        markReminderFailed(3);

        retrier.retryFailed();

        assertThat(emailsTo(member)).hasSize(1);
        assertThat(reminder().getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void aReminderForSomeoneWhoLostTheirSeatIsAbandoned() throws Exception {
        markReminderFailed(1);
        mockMvc.perform(delete("/api/groups/{g}/events/{e}/signups/me", group.id(), eventId).with(as(member)))
                .andExpect(status().isNoContent());

        retrier.retryFailed();

        assertThat(emailsTo(member)).hasSize(1);
        assertThat(reminder().getStatus()).isEqualTo(NotificationStatus.ABANDONED);
    }

    @Test
    void twoParallelRetriesSendOneEmail() {
        markReminderFailed(1);

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<CompletableFuture<Void>> runs = List.of(
                    CompletableFuture.runAsync(() -> retryAfter(start), executor),
                    CompletableFuture.runAsync(() -> retryAfter(start), executor));
            start.countDown();
            CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).join();
        }

        assertThat(emailsTo(member)).hasSize(2); // the first send and exactly one retry
    }

    private void markReminderFailed(int attempts) {
        jdbcClient.sql("update notifications set status = 'FAILED', sent_at = null, attempts = :attempts where id = :id")
                .param("attempts", attempts)
                .param("id", reminder().getId())
                .update();
    }

    private Notification reminder() {
        return notificationRepository.findByUserIdAndDedupKey(member.id(), ReminderScheduler.reminderKey(eventId))
                .orElseThrow();
    }

    private void retryAfter(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        retrier.retryFailed();
    }
}
