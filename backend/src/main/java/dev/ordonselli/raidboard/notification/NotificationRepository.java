package dev.ordonselli.raidboard.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Creates the notification unless this user already has one with the same key, and returns the
     * number of rows inserted (1 or 0).
     * <p>
     * This is what makes "exactly once" safe: the unique constraint on (user_id, dedup_key) decides,
     * atomically, inside PostgreSQL. A "check if it exists, then insert" in Java would let two runs
     * of the job both see "not there yet" and both send the email.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO notifications (user_id, type, payload, status, dedup_key)
            VALUES (:userId, :type, CAST(:payload AS jsonb), 'PENDING', :dedupKey)
            ON CONFLICT (user_id, dedup_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("type") String type, @Param("payload") String payload,
            @Param("dedupKey") String dedupKey);

    Optional<Notification> findByUserIdAndDedupKey(Long userId, String dedupKey);

    List<Notification> findAllByUserIdOrderByIdAsc(Long userId);

    /** Failed notifications with attempts left, oldest first. */
    List<Notification> findAllByStatusAndAttemptsLessThanOrderByIdAsc(NotificationStatus status, int attempts);

    /**
     * Takes a failed notification back for one more attempt and returns the number of rows changed (1 or 0).
     * Like {@link #insertIfAbsent}, PostgreSQL decides atomically: if two runs try to retry the same row,
     * only one of them gets 1 and sends the email.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE notifications SET status = 'PENDING', attempts = attempts + 1
            WHERE id = :id AND status = 'FAILED' AND attempts < :maxAttempts
            """, nativeQuery = true)
    int claimForRetry(@Param("id") Long id, @Param("maxAttempts") int maxAttempts);

    @Modifying
    @Transactional
    @Query("update Notification n set n.status = :status, n.sentAt = :sentAt where n.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") NotificationStatus status,
            @Param("sentAt") Instant sentAt);
}
