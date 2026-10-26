package dev.ordonselli.raidboard.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.icegreen.greenmail.util.GreenMailUtil;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.ordonselli.raidboard.support.AbstractIntegrationTest;
import dev.ordonselli.raidboard.support.TestGroup;
import dev.ordonselli.raidboard.support.TestUser;

/** Emails for waitlist promotions (business rule 3) and reminders (business rule 7), read back from GreenMail. */
class NotificationIT extends AbstractIntegrationTest {

    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private ReminderScheduler reminderScheduler;

    @Autowired
    private NotificationRepository notificationRepository;

    private TestUser owner;
    private TestGroup group;

    @BeforeEach
    void setUp() throws Exception {
        owner = newUser("Owner");
        group = createGroup(owner, "Mail guild");
    }

    @Test
    void aPromotedUserGetsAnEmailInTheirLanguage() throws Exception {
        TestUser english = newUser("Emma", "en");
        joinGroup(english, group);
        Waitlisted waitlisted = fullEventWithWaitlisted(english);

        withdraw(owner, waitlisted.eventId());

        MimeMessage email = awaitSingleEmailTo(english);
        assertThat(email.getSubject()).isEqualTo("You got a seat: Raid night");
        String body = GreenMailUtil.getBody(email);
        assertThat(body).contains("Hi Emma,", "Mail guild",
                "http://localhost:4200/groups/" + group.id() + "/events/" + waitlisted.eventId());
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(notificationRepository.findByUserIdAndDedupKey(english.id(),
                        "promoted:signup:" + waitlisted.signupId()))
                        .get()
                        .returns(NotificationType.SIGNUP_PROMOTED, Notification::getType)
                        .returns(NotificationStatus.SENT, Notification::getStatus));
    }

    @Test
    void italianUsersGetTheItalianEmail() throws Exception {
        TestUser italian = newUser("Giulia", "it");
        joinGroup(italian, group);
        Waitlisted waitlisted = fullEventWithWaitlisted(italian);

        withdraw(owner, waitlisted.eventId());

        MimeMessage email = awaitSingleEmailTo(italian);
        assertThat(email.getSubject()).isEqualTo("Hai un posto: Raid night");
        assertThat(GreenMailUtil.getBody(email)).contains("Ciao Giulia,");
    }

    @Test
    void theReminderGoesToConfirmedParticipantsOfEventsStartingWithin24HoursOnlyOnce() throws Exception {
        TestUser confirmed = newUser("Confirmed");
        TestUser waitlisted = newUser("Waitlisted");
        joinGroup(confirmed, group);
        joinGroup(waitlisted, group);
        String soon = createPublishedEvent(owner, group, eventJson(inHours(2), false, 0, "Tank", 1));
        signUp(confirmed, group, idOf(soon), slotIdOf(soon, 0));
        signUp(waitlisted, group, idOf(soon), slotIdOf(soon, 0));
        String later = createPublishedEvent(owner, group, eventJson(inHours(72), false, 0, "Tank", 1));
        signUp(confirmed, group, idOf(later), slotIdOf(later, 0));

        reminderScheduler.sendReminders();
        reminderScheduler.sendReminders();

        List<MimeMessage> emails = emailsTo(confirmed);
        assertThat(emails).hasSize(1);
        assertThat(emails.getFirst().getSubject()).isEqualTo("Promemoria: Raid night inizia a breve");
        assertThat(emailsTo(waitlisted)).isEmpty();
        assertThat(notificationRepository.findAllByUserIdOrderByIdAsc(confirmed.id()))
                .singleElement()
                .returns(ReminderScheduler.reminderKey(idOf(soon)), Notification::getDedupKey)
                .returns(NotificationStatus.SENT, Notification::getStatus);
    }

    /**
     * Two runs at the same moment (e.g. two backend instances) both find the same sign-up "without a
     * reminder". The unique key in the database lets only one of them send it.
     */
    @Test
    void twoParallelRunsOfTheReminderJobSendOneEmail() throws Exception {
        TestUser confirmed = newUser("Confirmed");
        joinGroup(confirmed, group);
        String soon = createPublishedEvent(owner, group, eventJson(inHours(3), false, 0, "Tank", 1));
        signUp(confirmed, group, idOf(soon), slotIdOf(soon, 0));

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<CompletableFuture<Void>> runs = List.of(
                    CompletableFuture.runAsync(() -> runAfter(start), executor),
                    CompletableFuture.runAsync(() -> runAfter(start), executor));
            start.countDown();
            CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).join();
        }

        assertThat(emailsTo(confirmed)).hasSize(1);
    }

    private record Waitlisted(Long eventId, Long signupId) {
    }

    /** An event with one seat: the owner takes it, {@code user} ends up on the waitlist. */
    private Waitlisted fullEventWithWaitlisted(TestUser user) throws Exception {
        String event = createPublishedEvent(owner, group, eventJson(inHours(24 * 7), false, 0, "Tank", 1));
        Long eventId = idOf(event);
        signUp(owner, group, eventId, slotIdOf(event, 0));
        return new Waitlisted(eventId, signUp(user, group, eventId, slotIdOf(event, 0)));
    }

    private void withdraw(TestUser user, Long eventId) throws Exception {
        mockMvc.perform(delete("/api/groups/{g}/events/{e}/signups/me", group.id(), eventId).with(as(user)))
                .andExpect(status().isNoContent());
    }

    private MimeMessage awaitSingleEmailTo(TestUser user) {
        await().atMost(ASYNC_TIMEOUT).until(() -> !emailsTo(user).isEmpty());
        return emailsTo(user).getFirst();
    }

    private void runAfter(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        reminderScheduler.sendReminders();
    }

    private static Instant inHours(int hours) {
        return Instant.now().plus(hours, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
    }
}
