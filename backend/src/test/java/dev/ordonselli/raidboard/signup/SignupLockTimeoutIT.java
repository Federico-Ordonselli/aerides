package dev.ordonselli.raidboard.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.ordonselli.raidboard.event.EventSlotRepository;
import dev.ordonselli.raidboard.support.AbstractIntegrationTest;
import dev.ordonselli.raidboard.support.TestGroup;
import dev.ordonselli.raidboard.support.TestUser;

/**
 * A sign-up stuck behind a slot lock that is never released gives up after {@code lock_timeout}
 * (2 s in the test profile) with a retryable 503, instead of holding its request thread forever.
 */
class SignupLockTimeoutIT extends AbstractIntegrationTest {

    @Autowired
    private EventSlotRepository slotRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void aSignupWaitingForAStuckLockFailsWithARetryableError() throws Exception {
        TestUser owner = newUser("Owner");
        TestUser member = newUser("Member");
        TestGroup group = createGroup(owner, "Lock timeout test");
        joinGroup(member, group);
        String event = createPublishedEvent(owner, group,
                eventJson(Instant.now().plus(Duration.ofDays(7)), false, 0, "Tank", 1));
        Long eventId = idOf(event);
        Long slotId = slotIdOf(event, 0);

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            // A "stuck" transaction: it holds the slot row lock until the test releases it
            Future<?> holder = executor.submit(() -> transaction.executeWithoutResult(status -> {
                slotRepository.findByIdForUpdate(slotId).orElseThrow();
                locked.countDown();
                try {
                    // Without lock_timeout the request below would wait all this time and then succeed
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            mockMvc.perform(post("/api/groups/{g}/events/{e}/signups", group.id(), eventId).with(as(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"slotId\": %d}".formatted(slotId)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                    .andExpect(jsonPath("$.title").value("Busy"));

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        }

        // Nothing was written by the failed attempt: once the lock is free, the same sign-up works
        signUp(member, group, eventId, slotId);
    }
}
