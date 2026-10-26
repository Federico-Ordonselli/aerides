package dev.ordonselli.raidboard.signup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to sign-up changes once they are committed.
 * <p>
 * {@code @TransactionalEventListener} runs after the commit by default (phase AFTER_COMMIT): if the
 * transaction rolls back, nobody is told about a promotion that never happened. For now it only logs;
 * Milestone 4 adds the email and the WebSocket message here.
 */
@Component
public class SignupNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SignupNotificationListener.class);

    @TransactionalEventListener
    public void onPromoted(SignupPromotedEvent event) {
        log.info("Sign-up {} of user {} promoted from the waitlist of event {}", event.signupId(), event.userId(),
                event.eventId());
    }
}
