package dev.ordonselli.aerides.signup;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import dev.ordonselli.aerides.event.Event;
import dev.ordonselli.aerides.event.EventSlot;
import dev.ordonselli.aerides.user.User;

@Entity
@Table(name = "signups")
public class Signup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false, updatable = false)
    private EventSlot slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignupStatus status;

    /** Sign-up order inside the slot: the waitlist is promoted from the lowest position. */
    @Column(nullable = false, updatable = false)
    private int position;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Signup() {
    }

    public Signup(Event event, EventSlot slot, User user, SignupStatus status, int position) {
        this.event = event;
        this.slot = slot;
        this.user = user;
        this.status = status;
        this.position = position;
    }

    public boolean isActive() {
        return SignupStatus.ACTIVE.contains(status);
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public EventSlot getSlot() {
        return slot;
    }

    public User getUser() {
        return user;
    }

    public SignupStatus getStatus() {
        return status;
    }

    public void setStatus(SignupStatus status) {
        this.status = status;
    }

    public int getPosition() {
        return position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
