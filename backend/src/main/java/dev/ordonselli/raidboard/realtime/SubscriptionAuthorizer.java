package dev.ordonselli.raidboard.realtime;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.ordonselli.raidboard.event.Event;
import dev.ordonselli.raidboard.event.EventRepository;
import dev.ordonselli.raidboard.event.EventStatus;
import dev.ordonselli.raidboard.group.Membership;
import dev.ordonselli.raidboard.group.MembershipRepository;
import dev.ordonselli.raidboard.group.MembershipRole;

/**
 * Decides which destinations a user may subscribe to. Whatever is not listed here is refused.
 * <p>
 * The rule for an event topic is the same as for reading the event over REST (business rule 10):
 * members of its group only, and drafts only for officers and owners.
 */
@Component
public class SubscriptionAuthorizer {

    public static final String NOTIFICATIONS_QUEUE = "/user/queue/notifications";
    private static final Pattern EVENT_TOPIC = Pattern.compile("/topic/events/(\\d{1,18})");

    private final EventRepository eventRepository;
    private final MembershipRepository membershipRepository;

    public SubscriptionAuthorizer(EventRepository eventRepository, MembershipRepository membershipRepository) {
        this.eventRepository = eventRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public boolean canSubscribe(Long userId, String destination) {
        if (destination == null) {
            return false;
        }
        // Spring rewrites it to a queue of this session only: nobody can read another user's queue
        if (destination.equals(NOTIFICATIONS_QUEUE)) {
            return true;
        }
        Matcher matcher = EVENT_TOPIC.matcher(destination);
        return matcher.matches() && canReadEvent(userId, Long.valueOf(matcher.group(1)));
    }

    private boolean canReadEvent(Long userId, Long eventId) {
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null) {
            return false;
        }
        MembershipRole role = membershipRepository.findByGroupIdAndUserId(event.getGroup().getId(), userId)
                .map(Membership::getRole)
                .orElse(null);
        if (role == null) {
            return false;
        }
        return event.getStatus() != EventStatus.DRAFT || role.isAtLeast(MembershipRole.OFFICER);
    }
}
