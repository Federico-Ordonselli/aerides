package dev.ordonselli.raidboard.notification;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import dev.ordonselli.raidboard.event.Event;
import dev.ordonselli.raidboard.user.User;

/**
 * Sends an email at most once per (user, key).
 * <ol>
 *   <li>Insert the notification row, unless the key is already taken: then someone already sent it.</li>
 *   <li>Send the email.</li>
 *   <li>Mark the row SENT or FAILED.</li>
 * </ol>
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
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, EmailComposer emailComposer,
            EmailSender emailSender, JsonMapper jsonMapper, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.emailComposer = emailComposer;
        this.emailSender = emailSender;
        this.jsonMapper = jsonMapper;
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
        try {
            emailSender.send(emailComposer.compose(type, recipient, event));
            notificationRepository.updateStatus(notificationId, NotificationStatus.SENT, clock.instant());
            return true;
        } catch (RuntimeException e) {
            // Not retried: for a reminder, a late duplicate is worse than a missing one
            log.warn("Notification {} ({}) could not be sent", notificationId, type, e);
            notificationRepository.updateStatus(notificationId, NotificationStatus.FAILED, null);
            return false;
        }
    }

    /** What the notification was about, stored as JSONB for later use (e.g. an in-app notification list). */
    private record Payload(Long groupId, Long eventId) {
    }
}
