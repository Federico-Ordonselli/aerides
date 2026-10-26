package dev.ordonselli.raidboard.signup;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.ordonselli.raidboard.event.EventCount;

public interface SignupRepository extends JpaRepository<Signup, Long> {

    boolean existsByEventIdAndUserIdAndStatusIn(Long eventId, Long userId, Collection<SignupStatus> statuses);

    long countBySlotIdAndStatus(Long slotId, SignupStatus status);

    long countBySlotIdAndStatusIn(Long slotId, Collection<SignupStatus> statuses);

    @Query("select coalesce(max(s.position), 0) from Signup s where s.slot.id = :slotId")
    int findMaxPositionBySlotId(@Param("slotId") Long slotId);

    /** The first {@code limit} sign-ups with this status, in FIFO order. */
    List<Signup> findAllBySlotIdAndStatusOrderByPositionAsc(Long slotId, SignupStatus status, Limit limit);

    Optional<Signup> findByEventIdAndUserIdAndStatusIn(Long eventId, Long userId, Collection<SignupStatus> statuses);

    Optional<Signup> findByIdAndEventId(Long id, Long eventId);

    /*
     * Only the slot id, not the Signup entity: the caller locks the slot first and loads the sign-up
     * afterwards, so it reads the sign-up's status as it is after any concurrent change has committed.
     */
    @Query("""
            select s.slot.id from Signup s
            where s.event.id = :eventId and s.user.id = :userId and s.status in :statuses
            """)
    Optional<Long> findSlotIdByEventIdAndUserIdAndStatusIn(@Param("eventId") Long eventId,
            @Param("userId") Long userId, @Param("statuses") Collection<SignupStatus> statuses);

    @Query("select s.slot.id from Signup s where s.id = :id and s.event.id = :eventId")
    Optional<Long> findSlotIdByIdAndEventId(@Param("id") Long id, @Param("eventId") Long eventId);

    @Query("""
            select s from Signup s join fetch s.user
            where s.event.id = :eventId and s.status in :statuses
            order by s.position
            """)
    List<Signup> findAllWithUserByEventIdAndStatusIn(@Param("eventId") Long eventId,
            @Param("statuses") Collection<SignupStatus> statuses);

    @Query("""
            select new dev.ordonselli.raidboard.event.EventCount(s.event.id, count(s))
            from Signup s where s.event.id in :eventIds and s.status = :status group by s.event.id
            """)
    List<EventCount> countByEventIdsAndStatus(@Param("eventIds") Collection<Long> eventIds,
            @Param("status") SignupStatus status);
}
