package dev.ordonselli.raidboard.signup;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.ordonselli.raidboard.event.EventSlot;

/**
 * Seat counting and waitlist promotion, shared by sign-ups (a confirmed user withdraws) and event
 * editing (a slot gets more seats).
 * <p>
 * Callers must hold the slot's row lock, otherwise two transactions could count the same free seat.
 * {@code MANDATORY} makes sure there is a transaction to hold it.
 */
@Service
public class WaitlistService {

    private final SignupRepository signupRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WaitlistService(SignupRepository signupRepository, ApplicationEventPublisher eventPublisher) {
        this.signupRepository = signupRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long freeSeats(EventSlot slot) {
        return slot.getCapacity() - signupRepository.countBySlotIdAndStatus(slot.getId(), SignupStatus.CONFIRMED);
    }

    /** Moves the first waitlisted users (FIFO by position) into the free seats of the slot (business rule 3). */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Signup> promote(EventSlot slot) {
        long freeSeats = freeSeats(slot);
        if (freeSeats <= 0) {
            return List.of();
        }
        List<Signup> promoted = signupRepository.findAllBySlotIdAndStatusOrderByPositionAsc(slot.getId(),
                SignupStatus.WAITLISTED, Limit.of((int) freeSeats));
        for (Signup signup : promoted) {
            signup.setStatus(SignupStatus.CONFIRMED);
            eventPublisher.publishEvent(new SignupPromotedEvent(slot.getEvent().getGroup().getId(),
                    slot.getEvent().getId(), slot.getEvent().getTitle(), signup.getId(), signup.getUser().getId()));
        }
        return promoted;
    }
}
