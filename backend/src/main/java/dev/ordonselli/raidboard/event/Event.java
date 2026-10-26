package dev.ordonselli.raidboard.event;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import dev.ordonselli.raidboard.common.error.ConflictException;
import dev.ordonselli.raidboard.game.Game;
import dev.ordonselli.raidboard.group.Group;
import dev.ordonselli.raidboard.user.User;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id")
    private Game game;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(name = "withdraw_deadline_hours", nullable = false)
    private int withdrawDeadlineHours;

    /** For display only, e.g. "FREQ=WEEKLY;COUNT=8": the occurrences are real rows sharing {@link #seriesId}. */
    @Column(name = "recurrence_rule", updatable = false)
    private String recurrenceRule;

    @Column(name = "series_id", updatable = false)
    private UUID seriesId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Optimistic locking: Hibernate adds "AND version = ?" to every UPDATE and increments it.
     * If two officers save the same event at the same time, the second UPDATE matches no row and fails.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<EventSlot> slots = new ArrayList<>();

    protected Event() {
    }

    public Event(Group group, User createdBy, String title, Instant startsAt, int durationMinutes) {
        this.group = group;
        this.createdBy = createdBy;
        this.title = title;
        this.startsAt = startsAt;
        this.durationMinutes = durationMinutes;
    }

    public EventSlot addSlot(String roleName, int capacity) {
        EventSlot slot = new EventSlot(this, roleName, capacity);
        slots.add(slot);
        return slot;
    }

    public void removeSlot(EventSlot slot) {
        slots.remove(slot);
    }

    public void publish() {
        requireStatus(EventStatus.DRAFT, "Only a draft can be published");
        status = EventStatus.PUBLISHED;
    }

    public void cancel() {
        if (status != EventStatus.DRAFT && status != EventStatus.PUBLISHED) {
            throw new ConflictException("Only a draft or published event can be cancelled");
        }
        status = EventStatus.CANCELLED;
    }

    public void complete() {
        requireStatus(EventStatus.PUBLISHED, "Only a published event can be completed");
        status = EventStatus.COMPLETED;
    }

    /** Cancelled and completed events are history: they can't be edited any more. */
    public boolean isEditable() {
        return status == EventStatus.DRAFT || status == EventStatus.PUBLISHED;
    }

    /** After this instant confirmed participants can no longer withdraw (business rule 5). */
    public Instant getWithdrawDeadline() {
        return startsAt.minus(Duration.ofHours(withdrawDeadlineHours));
    }

    private void requireStatus(EventStatus expected, String message) {
        if (status != expected) {
            throw new ConflictException(message);
        }
    }

    public Long getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public EventStatus getStatus() {
        return status;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public int getWithdrawDeadlineHours() {
        return withdrawDeadlineHours;
    }

    public void setWithdrawDeadlineHours(int withdrawDeadlineHours) {
        this.withdrawDeadlineHours = withdrawDeadlineHours;
    }

    public String getRecurrenceRule() {
        return recurrenceRule;
    }

    public UUID getSeriesId() {
        return seriesId;
    }

    public void setSeries(UUID seriesId, String recurrenceRule) {
        this.seriesId = seriesId;
        this.recurrenceRule = recurrenceRule;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    /** Read-only view: slots are added and removed through {@link #addSlot} and {@link #removeSlot}. */
    public List<EventSlot> getSlots() {
        return Collections.unmodifiableList(slots);
    }
}
