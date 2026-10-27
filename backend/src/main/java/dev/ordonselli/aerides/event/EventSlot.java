package dev.ordonselli.aerides.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A role inside an event with a number of seats, e.g. "Tank" × 2.
 * <p>
 * Sign-ups lock this row ({@code SELECT ... FOR UPDATE}) before counting seats: the slot is the
 * unit of concurrency, so two people taking the last seat of the same slot are serialized.
 */
@Entity
@Table(name = "event_slots")
public class EventSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Column(nullable = false)
    private int capacity;

    protected EventSlot() {
    }

    EventSlot(Event event, String roleName, int capacity) {
        this.event = event;
        this.roleName = roleName;
        this.capacity = capacity;
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }
}
