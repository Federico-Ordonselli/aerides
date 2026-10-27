package dev.ordonselli.aerides.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.ordonselli.aerides.signup.SignupPromotedEvent;
import dev.ordonselli.aerides.signup.SignupRepository;

/**
 * Emails a user who got a seat from the waitlist (business rule 3).
 * <p>
 * After the commit, so no email goes out for a promotion that was rolled back; {@code @Async}, so the
 * user who withdrew doesn't wait for the SMTP server.
 */
@Component
public class PromotionEmailListener {

    private final SignupRepository signupRepository;
    private final NotificationService notificationService;

    public PromotionEmailListener(SignupRepository signupRepository, NotificationService notificationService) {
        this.signupRepository = signupRepository;
        this.notificationService = notificationService;
    }

    @Async
    @TransactionalEventListener
    public void onPromoted(SignupPromotedEvent event) {
        // A sign-up is promoted at most once, so its id is a natural deduplication key
        signupRepository.findWithUserAndEventById(event.signupId()).ifPresent(signup ->
                notificationService.sendOnce(NotificationType.SIGNUP_PROMOTED, signup.getUser(), signup.getEvent(),
                        "promoted:signup:" + signup.getId()));
    }
}
