package dev.ordonselli.aerides.notification;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import dev.ordonselli.aerides.event.Event;
import dev.ordonselli.aerides.user.User;

/**
 * Sends an email at most once per (user, key).
 * <ol>
 *   <li>Insert the notification row, unless the key is already taken: then someone already sent it.</li>
 *   <li>Send the email.</li>
 *   <li>Mark the row SENT or FAILED.</li>
 * </ol>
 * A FAILED row can be taken back for another attempt with {@link #retry}, which claims it atomically
 * in the same way, so two runs never send the same retry twice.
 * No method is {@code @Transactional}: each repository call commits on its own, so the row is claimed
 * before the slow SMTP call and no database connection is held while talking to the mail server.
 */
@Service
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final EmailComposer emailComposer;
    private final EmailSender emailSender;
    private final JsonMapper jsonMapper;
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, EmailComposer emailComposer,
            EmailSender emailSender, JsonMapper jsonMapper, NotificationProperties properties, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.emailComposer = emailComposer;
        this.emailSender = emailSender;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Emails {@code recipient} about {@code event} (group loaded), unless a notification with
     * {@code dedupKey} already exists for them. Returns true if this call sent it.
     */
    public boolean sendOnce(NotificationType type, User recipient, Event event, String dedupKey) {
        String payload = jsonMapper.writeValueAsString(new Payload(event.getGroup().getId(), event.getId()));
        if (notificationRepository.insertIfAbsent(recipient.getId(), type.name(), payload, dedupKey) == 0) {
            return false;
        }
        Long notificationId = notificationRepository.findByUserIdAndDedupKey(recipient.getId(), dedupKey)
                .orElseThrow()
                .getId();
        return deliver(notificationId, type, recipient, event);
    }

    /**
     * One more attempt at a FAILED notification. Returns false if it has no attempts left, if another run
     * took it first, or if the email failed again (then it stays FAILED for the next retry).
     */
    public boolean retry(Notification notification, User recipient, Event event) {
        if (notificationRepository.claimForRetry(notification.getId(), properties.maxAttempts()) == 0) {
            return false;
        }
        return deliver(notification.getId(), notification.getType(), recipient, event);
    }

    /** A failed notification that is no longer worth sending: it will never be retried. */
    public void abandon(Notification notification) {
        notificationRepository.updateStatus(notification.getId(), NotificationStatus.ABANDONED, null);
    }

    private boolean deliver(Long notificationId, NotificationType type, User recipient, Event event) {
        try {
            emailSender.send(emailComposer.compose(type, recipient, event));
            notificationRepository.updateStatus(notificationId, NotificationStatus.SENT, clock.instant());
            return true;
        } catch (RuntimeException e) {
            log.warn("Notification {} ({}) could not be sent", notificationId, type, e);
            notificationRepository.updateStatus(notificationId, NotificationStatus.FAILED, null);
            return false;
        }
    }

    /** What the notification was about, stored as JSONB for later use (e.g. an in-app notification list). */
    private record Payload(Long groupId, Long eventId) {
    }
}
