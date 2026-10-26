package dev.ordonselli.raidboard.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.ordonselli.raidboard.event.EventChangedEvent;
import dev.ordonselli.raidboard.signup.SignupPromotedEvent;

/**
 * Turns domain events into WebSocket messages, after the commit (business rule 3): clients reload
 * data that is already in the database, and nobody is told about a change that was rolled back.
 */
@Component
public class RealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener
    public void onEventChanged(EventChangedEvent event) {
        send(() -> messagingTemplate.convertAndSend("/topic/events/" + event.eventId(),
                new EventUpdateMessage(event.eventId(), event.type())));
    }

    /** The principal name of a WebSocket session is the user id (the JWT subject), see StompAuthInterceptor. */
    @TransactionalEventListener
    public void onPromoted(SignupPromotedEvent event) {
        send(() -> messagingTemplate.convertAndSendToUser(event.userId().toString(), "/queue/notifications",
                new UserNotificationMessage(UserNotificationMessage.Type.SIGNUP_PROMOTED, event.groupId(),
                        event.eventId(), event.eventTitle())));
    }

    /** Live updates are a convenience: a failure is logged and never reaches the request that caused it. */
    private static void send(Runnable sending) {
        try {
            sending.run();
        } catch (MessagingException e) {
            log.warn("WebSocket message not sent", e);
        }
    }
}
