package dev.ordonselli.raidboard.event;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The {@code ForUpdate} methods run {@code SELECT ... FOR UPDATE}: the row stays locked until the
 * transaction ends, and any other transaction asking for the same lock waits. This is how concurrent
 * sign-ups for the last seat are serialized (business rule 4).
 */
public interface EventSlotRepository extends JpaRepository<EventSlot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EventSlot s where s.id = :id and s.event.id = :eventId")
    Optional<EventSlot> findByIdAndEventIdForUpdate(@Param("id") Long id, @Param("eventId") Long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EventSlot s join fetch s.event where s.id = :id")
    Optional<EventSlot> findByIdForUpdate(@Param("id") Long id);

    /** Locks all the slots of an event, always in id order: two transactions locking in the same order can't deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EventSlot s where s.event.id = :eventId order by s.id")
    List<EventSlot> findAllByEventIdForUpdate(@Param("eventId") Long eventId);

    @Query("""
            select new dev.ordonselli.raidboard.event.EventCount(s.event.id, sum(s.capacity))
            from EventSlot s where s.event.id in :eventIds group by s.event.id
            """)
    List<EventCount> sumCapacityByEventIds(@Param("eventIds") Collection<Long> eventIds);
}
