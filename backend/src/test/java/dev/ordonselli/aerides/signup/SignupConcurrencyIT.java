package dev.ordonselli.aerides.signup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.ordonselli.aerides.support.AbstractIntegrationTest;
import dev.ordonselli.aerides.support.TestGroup;
import dev.ordonselli.aerides.support.TestUser;

/**
 * Business rule 4: concurrent sign-ups for the last seats must never overbook a slot.
 * <p>
 * Many threads wait on a latch and then sign up at the same moment, each in its own transaction.
 * Without the {@code SELECT ... FOR UPDATE} on the slot, several of them count the same free seat
 * and all become CONFIRMED (and get the same position). With the lock they run one after the other.
 */
class SignupConcurrencyIT extends AbstractIntegrationTest {

    private static final int CAPACITY = 3;
    private static final int PARALLEL_SIGNUPS = 20;

    @Autowired
    private SignupService signupService;

    @Autowired
    private SignupRepository signupRepository;

    @Test
    void parallelSignupsNeverExceedTheSlotCapacity() throws Exception {
        TestUser owner = newUser("Owner");
        TestGroup group = createGroup(owner, "Concurrency test");
        String event = createPublishedEvent(owner, group,
                eventJson(Instant.now().plus(Duration.ofDays(7)), false, 0, "Tank", CAPACITY));
        Long eventId = idOf(event);
        Long slotId = ((Number) JsonPath.read(event, "$.slots[0].id")).longValue();
        List<TestUser> users = new ArrayList<>();
        for (int i = 0; i < PARALLEL_SIGNUPS; i++) {
            TestUser user = newUser("Player " + i);
            joinGroup(user, group);
            users.add(user);
        }

        CountDownLatch start = new CountDownLatch(1);
        List<Future<SignupResponse>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_SIGNUPS)) {
            for (TestUser user : users) {
                results.add(executor.submit(() -> {
                    start.await(); // every thread waits here, then they all go at once
                    return signupService.signUp(group.id(), eventId, user.id(), slotId);
                }));
            }
            start.countDown();
            for (Future<SignupResponse> result : results) {
                result.get(30, TimeUnit.SECONDS); // rethrows any exception from the thread
            }
        }

        List<Signup> signups = signupRepository.findAllWithUserByEventIdAndStatusIn(eventId, SignupStatus.ACTIVE);
        assertThat(signups).hasSize(PARALLEL_SIGNUPS);
        assertThat(signups).filteredOn(signup -> signup.getStatus() == SignupStatus.CONFIRMED).hasSize(CAPACITY);
        assertThat(signups).filteredOn(signup -> signup.getStatus() == SignupStatus.WAITLISTED)
                .hasSize(PARALLEL_SIGNUPS - CAPACITY);
        // FIFO order is intact: every sign-up got its own position, 1 to N
        assertThat(signups).extracting(Signup::getPosition)
                .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, PARALLEL_SIGNUPS)
                        .boxed().toList());
    }
}
