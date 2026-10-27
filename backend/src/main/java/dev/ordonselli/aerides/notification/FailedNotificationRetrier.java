package dev.ordonselli.aerides.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.ordonselli.aerides.event.Event;
import dev.ordonselli.aerides.event.EventRepository;
import dev.ordonselli.aerides.event.EventStatus;
import dev.ordonselli.aerides.signup.SignupRepository;
import dev.ordonselli.aerides.signup.SignupStatus;
import dev.ordonselli.aerides.user.User;
import dev.ordonselli.aerides.user.UserRepository;

/**
 * Tries again the emails that failed (mail server down), up to {@code max-attempts} in total.
 * <p>
 * Every email is about an event, and is useful only before it starts: a reminder or a "you got a seat"
 * email after the start is noise. So a failed email is retried only while its event is published, hasn't
 * started, and the user still holds a seat; otherwise it is ABANDONED and never looked at again.
 * <p>
 * The trade-off: a send can fail <em>after</em> the mail server accepted the message (a timeout on the
 * reply), and then the retry is a duplicate. With a few attempts at most, before the event, an occasional
 * duplicate is better than a reminder that never arrives.
 */
@Component
public class FailedNotificationRetrier {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final SignupRepository signupRepository;
    private final NotificationProperties properties;
    private final Clock clock;

    public FailedNotificationRetrier(NotificationRepository notificationRepository,
            NotificationService notificationService, EventRepository eventRepository, UserRepository userRepository,
            SignupRepository signupRepository, NotificationProperties properties, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.signupRepository = signupRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${aerides.notifications.retry-interval}",
            initialDelayString = "${aerides.notifications.retry-interval}")
    public void retryFailed() {
        Instant now = clock.instant();
        List<Notification> failed = notificationRepository.findAllByStatusAndAttemptsLessThanOrderByIdAsc(
                NotificationStatus.FAILED, properties.maxAttempts());
        for (Notification notification : failed) {
            Optional<Event> event = eventIdOf(notification).flatMap(eventRepository::findWithGroupById);
            Optional<User> recipient = userRepository.findById(notification.getUserId());
            if (event.isPresent() && recipient.isPresent() && stillUseful(notification, event.get(), now)) {
                notificationService.retry(notification, recipient.get(), event.get());
            } else {
                notificationService.abandon(notification);
            }
        }
    }

    /** The cut-off: a published event that hasn't started, where the user still has a confirmed seat. */
    private boolean stillUseful(Notification notification, Event event, Instant now) {
        return event.getStatus() == EventStatus.PUBLISHED
                && event.getStartsAt().isAfter(now)
                && signupRepository.existsByEventIdAndUserIdAndStatusIn(event.getId(), notification.getUserId(),
                        List.of(SignupStatus.CONFIRMED));
    }

    private static Optional<Long> eventIdOf(Notification notification) {
        return notification.getPayload().get("eventId") instanceof Number eventId
                ? Optional.of(eventId.longValue())
                : Optional.empty();
    }
}
