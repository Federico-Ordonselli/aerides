package dev.ordonselli.aerides.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.ordonselli.aerides.event.EventStatus;
import dev.ordonselli.aerides.signup.Signup;
import dev.ordonselli.aerides.signup.SignupRepository;
import dev.ordonselli.aerides.signup.SignupStatus;

/**
 * Reminds confirmed participants of PUBLISHED events that start within the lead time (24h by default),
 * exactly once per user per event (business rule 7).
 * <p>
 * The job runs every few minutes and looks at the whole window, not at "exactly 24h from now": a run
 * that is late or skipped (restart, deploy) is caught up by the next one. Users who sign up less than
 * 24h before the start get their reminder at the next run. "Exactly once" does not depend on timing:
 * see {@link NotificationRepository#insertIfAbsent}.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final SignupRepository signupRepository;
    private final NotificationService notificationService;
    private final NotificationProperties properties;
    private final Clock clock;

    public ReminderScheduler(SignupRepository signupRepository, NotificationService notificationService,
            NotificationProperties properties, Clock clock) {
        this.signupRepository = signupRepository;
        this.notificationService = notificationService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${aerides.notifications.reminder-interval}",
            initialDelayString = "${aerides.notifications.reminder-interval}")
    public void sendReminders() {
        Instant now = clock.instant();
        List<Signup> due = signupRepository.findWithoutReminder(EventStatus.PUBLISHED, SignupStatus.CONFIRMED, now,
                now.plus(properties.reminderLeadTime()));
        int sent = 0;
        for (Signup signup : due) {
            if (notificationService.sendOnce(NotificationType.EVENT_REMINDER, signup.getUser(), signup.getEvent(),
                    reminderKey(signup.getEvent().getId()))) {
                sent++;
            }
        }
        if (!due.isEmpty()) {
            log.info("Reminder job: {} due, {} sent", due.size(), sent);
        }
    }

    public static String reminderKey(Long eventId) {
        return "reminder:event:" + eventId;
    }
}
