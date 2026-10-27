package dev.ordonselli.aerides.signup;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.ordonselli.aerides.audit.AuditAction;
import dev.ordonselli.aerides.audit.AuditEntityType;
import dev.ordonselli.aerides.audit.AuditService;
import dev.ordonselli.aerides.common.error.BusinessRuleException;
import dev.ordonselli.aerides.common.error.ConflictException;
import dev.ordonselli.aerides.common.error.NotFoundException;
import dev.ordonselli.aerides.event.Event;
import dev.ordonselli.aerides.event.EventAlmostFullEvent;
import dev.ordonselli.aerides.event.EventChangeType;
import dev.ordonselli.aerides.event.EventChangedEvent;
import dev.ordonselli.aerides.event.EventRepository;
import dev.ordonselli.aerides.event.EventSlot;
import dev.ordonselli.aerides.event.EventSlotRepository;
import dev.ordonselli.aerides.event.EventStatus;
import dev.ordonselli.aerides.group.MembershipEndedEvent;
import dev.ordonselli.aerides.user.UserRepository;

/**
 * Sign-up, withdraw, approve and reject.
 * <p>
 * Every method that looks at seats first locks the slot row ({@code SELECT ... FOR UPDATE}), so
 * concurrent requests on the same slot run one after the other and never overbook it (business rule 4).
 */
@Service
public class SignupService {

    private final EventRepository eventRepository;
    private final EventSlotRepository slotRepository;
    private final SignupRepository signupRepository;
    private final UserRepository userRepository;
    private final WaitlistService waitlistService;
    private final SignupMapper signupMapper;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public SignupService(EventRepository eventRepository, EventSlotRepository slotRepository,
            SignupRepository signupRepository, UserRepository userRepository, WaitlistService waitlistService,
            SignupMapper signupMapper, AuditService auditService, ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.eventRepository = eventRepository;
        this.slotRepository = slotRepository;
        this.signupRepository = signupRepository;
        this.userRepository = userRepository;
        this.waitlistService = waitlistService;
        this.signupMapper = signupMapper;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Signs the user up for a slot: CONFIRMED if there is a free seat, WAITLISTED if the slot is full
     * (business rule 2), PENDING if the event requires approval (business rule 6).
     */
    @Transactional
    public SignupResponse signUp(Long groupId, Long eventId, Long userId, Long slotId) {
        Event event = requireOpenEvent(groupId, eventId);
        if (!event.getStartsAt().isAfter(clock.instant())) {
            throw new BusinessRuleException("The event has already started");
        }
        // Waits here if another sign-up for the same slot is running, until that transaction ends
        EventSlot slot = slotRepository.findByIdAndEventIdForUpdate(slotId, eventId)
                .orElseThrow(() -> new NotFoundException("Slot not found"));
        // A clear message for the common case. The unique index uk_signups_active_user_event is the real
        // guarantee: two parallel requests from the same user for different slots don't share a lock.
        if (signupRepository.existsByEventIdAndUserIdAndStatusIn(eventId, userId, SignupStatus.ACTIVE)) {
            throw new ConflictException("You are already signed up for this event");
        }

        SignupStatus status;
        if (event.isRequiresApproval()) {
            status = SignupStatus.PENDING;
        } else if (waitlistService.freeSeats(slot) > 0) {
            status = SignupStatus.CONFIRMED;
        } else {
            status = SignupStatus.WAITLISTED;
        }
        int position = signupRepository.findMaxPositionBySlotId(slotId) + 1;
        Signup signup = signupRepository.save(
                new Signup(event, slot, userRepository.getReferenceById(userId), status, position));
        publishSignupsChanged(event, status);
        return signupMapper.toResponse(signup);
    }

    /**
     * Withdraws the user's active sign-up. Confirmed users can't withdraw after the deadline
     * (business rule 5); their seat goes to the first waitlisted user in the same transaction (rule 3).
     * Waitlisted and pending users hold no seat, so they can always leave.
     */
    @Transactional
    public void withdraw(Long groupId, Long eventId, Long userId) {
        Event event = requireOpenEvent(groupId, eventId);
        Long slotId = signupRepository.findSlotIdByEventIdAndUserIdAndStatusIn(eventId, userId, SignupStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("You are not signed up for this event"));
        EventSlot slot = lockSlot(slotId, eventId);
        // Loaded after the lock, so its status is current (an officer may have just approved it)
        Signup signup = signupRepository.findByEventIdAndUserIdAndStatusIn(eventId, userId, SignupStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("You are not signed up for this event"));

        boolean hadSeat = signup.getStatus() == SignupStatus.CONFIRMED;
        if (hadSeat && clock.instant().isAfter(event.getWithdrawDeadline())) {
            throw new BusinessRuleException("The withdrawal deadline has passed: contact an officer");
        }
        signup.setStatus(SignupStatus.WITHDRAWN);
        if (hadSeat) {
            waitlistService.promote(slot);
        }
        publishSignupsChanged(event, SignupStatus.WITHDRAWN);
    }

    /**
     * A user who left the group or was removed gives up their sign-ups on its published events that haven't
     * started; their seats go to the waitlist. A plain {@code @EventListener} runs synchronously, inside the
     * transaction that deletes the membership, so the membership and its sign-ups go away together.
     * <p>
     * The withdrawal deadline doesn't apply: someone who is no longer a member can't keep a seat.
     * Sign-ups on events that have started or are completed stay, because they are attendance history.
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void withdrawFromUpcomingEvents(MembershipEndedEvent membershipEnded) {
        Long userId = membershipEnded.userId();
        List<Long> slotIds = signupRepository.findSlotIdsOfUpcomingSignups(membershipEnded.groupId(), userId,
                SignupStatus.ACTIVE, EventStatus.PUBLISHED, clock.instant());
        // In id order, like every multi-slot lock: two transactions locking in the same order can't deadlock
        for (Long slotId : slotIds) {
            EventSlot slot = slotRepository.findByIdForUpdate(slotId)
                    .orElseThrow(() -> new NotFoundException("Slot not found"));
            // Loaded after the lock, so its status is current
            signupRepository.findBySlotIdAndUserIdAndStatusIn(slotId, userId, SignupStatus.ACTIVE)
                    .ifPresent(signup -> {
                        boolean hadSeat = signup.getStatus() == SignupStatus.CONFIRMED;
                        signup.setStatus(SignupStatus.WITHDRAWN);
                        if (hadSeat) {
                            waitlistService.promote(slot);
                        }
                        publishSignupsChanged(slot.getEvent(), SignupStatus.WITHDRAWN);
                    });
        }
    }

    /** Approves a pending sign-up: it takes a seat if there is one, otherwise it joins the waitlist. */
    @Transactional
    public SignupResponse approve(Long groupId, Long eventId, Long signupId, Long actorId) {
        Event event = requireOpenEvent(groupId, eventId);
        EventSlot slot = lockSlotOfSignup(signupId, eventId);
        Signup signup = requirePending(signupId, eventId);
        signup.setStatus(waitlistService.freeSeats(slot) > 0 ? SignupStatus.CONFIRMED : SignupStatus.WAITLISTED);
        auditService.record(groupId, actorId, AuditAction.SIGNUP_APPROVED, AuditEntityType.SIGNUP, signupId,
                Map.of("eventId", eventId, "userId", signup.getUser().getId(), "status", signup.getStatus()));
        publishSignupsChanged(event, signup.getStatus());
        return signupMapper.toResponse(signup);
    }

    @Transactional
    public SignupResponse reject(Long groupId, Long eventId, Long signupId, Long actorId) {
        Event event = requireOpenEvent(groupId, eventId);
        lockSlotOfSignup(signupId, eventId);
        Signup signup = requirePending(signupId, eventId);
        signup.setStatus(SignupStatus.REJECTED);
        auditService.record(groupId, actorId, AuditAction.SIGNUP_REJECTED, AuditEntityType.SIGNUP, signupId,
                Map.of("eventId", eventId, "userId", signup.getUser().getId()));
        publishSignupsChanged(event, SignupStatus.REJECTED);
        return signupMapper.toResponse(signup);
    }

    /**
     * Tells the listeners (WebSocket, Discord) what happened. They run after the commit, so nothing
     * is announced if this transaction rolls back.
     */
    private void publishSignupsChanged(Event event, SignupStatus newStatus) {
        Long groupId = event.getGroup().getId();
        eventPublisher.publishEvent(new EventChangedEvent(groupId, event.getId(), EventChangeType.SIGNUPS));
        // Only a new confirmed seat can bring the event to "one seat left" (business rule 9)
        if (newStatus == SignupStatus.CONFIRMED && freeSeats(event) == 1) {
            eventPublisher.publishEvent(new EventAlmostFullEvent(groupId, event.getId()));
        }
    }

    private long freeSeats(Event event) {
        long capacity = event.getSlots().stream().mapToLong(EventSlot::getCapacity).sum();
        return capacity - signupRepository.countByEventIdAndStatus(event.getId(), SignupStatus.CONFIRMED);
    }

    /** Only published events take sign-up changes. Drafts are reported as not found, like for reads. */
    private Event requireOpenEvent(Long groupId, Long eventId) {
        Event event = eventRepository.findByIdAndGroupId(eventId, groupId)
                .filter(found -> found.getStatus() != EventStatus.DRAFT)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ConflictException("The event is " + event.getStatus().name().toLowerCase(Locale.ROOT)
                    + ": sign-ups are closed");
        }
        return event;
    }

    private EventSlot lockSlotOfSignup(Long signupId, Long eventId) {
        Long slotId = signupRepository.findSlotIdByIdAndEventId(signupId, eventId)
                .orElseThrow(() -> new NotFoundException("Sign-up not found"));
        return lockSlot(slotId, eventId);
    }

    private EventSlot lockSlot(Long slotId, Long eventId) {
        return slotRepository.findByIdAndEventIdForUpdate(slotId, eventId)
                .orElseThrow(() -> new NotFoundException("Slot not found"));
    }

    private Signup requirePending(Long signupId, Long eventId) {
        Signup signup = signupRepository.findByIdAndEventId(signupId, eventId)
                .orElseThrow(() -> new NotFoundException("Sign-up not found"));
        if (signup.getStatus() != SignupStatus.PENDING) {
            throw new ConflictException("Only a pending sign-up can be approved or rejected");
        }
        return signup;
    }
}
