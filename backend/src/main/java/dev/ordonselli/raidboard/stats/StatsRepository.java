package dev.ordonselli.raidboard.stats;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read-only reporting queries, written in plain SQL.
 * <p>
 * Aggregations like these return numbers, not entities: loading events and sign-ups through JPA
 * just to count them in Java would be slower and harder to read than one GROUP BY. {@link JdbcClient}
 * maps each row straight to a record.
 * <p>
 * Every query takes a period [from, to) on the event's start time. Only COMPLETED events count for
 * attendance and roles: a published event may still change, a cancelled one never took place.
 */
@Repository
public class StatsRepository {

    private final JdbcClient jdbcClient;

    public StatsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * One row per current member. Events before the member joined are not counted: nobody should
     * look absent from raids that happened before they were in the group.
     */
    public List<MemberAttendance> attendance(Long groupId, Instant from, Instant to) {
        return jdbcClient.sql("""
                        SELECT u.id, u.display_name,
                          (SELECT count(*) FROM events e
                            WHERE e.group_id = m.group_id AND e.status = 'COMPLETED'
                              AND e.starts_at >= m.joined_at
                              AND e.starts_at >= :from AND e.starts_at < :to) AS completed_events,
                          (SELECT count(*) FROM signups s JOIN events e ON e.id = s.event_id
                            WHERE s.user_id = m.user_id AND e.group_id = m.group_id
                              AND e.status = 'COMPLETED' AND s.status = 'CONFIRMED'
                              AND e.starts_at >= m.joined_at
                              AND e.starts_at >= :from AND e.starts_at < :to) AS attended,
                          (SELECT count(*) FROM signups s JOIN events e ON e.id = s.event_id
                            WHERE s.user_id = m.user_id AND e.group_id = m.group_id
                              AND s.status = 'WITHDRAWN'
                              AND e.starts_at >= :from AND e.starts_at < :to) AS withdrawals
                        FROM memberships m JOIN users u ON u.id = m.user_id
                        WHERE m.group_id = :groupId
                        ORDER BY lower(u.display_name), u.id
                        """)
                .param("groupId", groupId)
                .param("from", toTimestamp(from))
                .param("to", toTimestamp(to))
                .query((rs, rowNum) -> MemberAttendance.of(rs.getLong("id"), rs.getString("display_name"),
                        rs.getLong("completed_events"), rs.getLong("attended"), rs.getLong("withdrawals")))
                .list();
    }

    /**
     * Completed and cancelled events per month. The month is taken in the caller's time zone: an event
     * on 31 October at 23:30 UTC is a November event for someone in Rome.
     * Months without events are not returned; the service fills them in.
     */
    public List<MonthlyEventCount> eventsPerMonth(Long groupId, Instant from, Instant to, String zone) {
        return jdbcClient.sql("""
                        SELECT to_char(e.starts_at AT TIME ZONE :zone, 'YYYY-MM') AS month,
                               count(*) FILTER (WHERE e.status = 'COMPLETED') AS completed,
                               count(*) FILTER (WHERE e.status = 'CANCELLED') AS cancelled
                        FROM events e
                        WHERE e.group_id = :groupId AND e.status IN ('COMPLETED', 'CANCELLED')
                          AND e.starts_at >= :from AND e.starts_at < :to
                        GROUP BY month
                        ORDER BY month
                        """)
                .param("groupId", groupId)
                .param("from", toTimestamp(from))
                .param("to", toTimestamp(to))
                .param("zone", zone)
                .query((rs, rowNum) -> new MonthlyEventCount(YearMonth.parse(rs.getString("month")),
                        rs.getLong("completed"), rs.getLong("cancelled")))
                .list();
    }

    /**
     * Roles with the most empty seats on completed events. Role names are free text typed for each
     * event, so "Tank" and "tank " are grouped together.
     */
    public List<UnfilledRole> unfilledRoles(Long groupId, Instant from, Instant to, int limit) {
        return jdbcClient.sql("""
                        SELECT min(trim(sl.role_name)) AS role_name,
                               count(*) AS events,
                               sum(sl.capacity) AS capacity,
                               sum(coalesce(c.confirmed, 0)) AS confirmed
                        FROM event_slots sl
                        JOIN events e ON e.id = sl.event_id
                        LEFT JOIN (SELECT slot_id, count(*) AS confirmed FROM signups
                                   WHERE status = 'CONFIRMED' GROUP BY slot_id) c ON c.slot_id = sl.id
                        WHERE e.group_id = :groupId AND e.status = 'COMPLETED'
                          AND e.starts_at >= :from AND e.starts_at < :to
                        GROUP BY lower(trim(sl.role_name))
                        HAVING sum(sl.capacity) > sum(coalesce(c.confirmed, 0))
                        ORDER BY sum(sl.capacity) - sum(coalesce(c.confirmed, 0)) DESC, role_name
                        LIMIT :limit
                        """)
                .param("groupId", groupId)
                .param("from", toTimestamp(from))
                .param("to", toTimestamp(to))
                .param("limit", limit)
                .query((rs, rowNum) -> {
                    long capacity = rs.getLong("capacity");
                    long confirmed = rs.getLong("confirmed");
                    return new UnfilledRole(rs.getString("role_name"), rs.getLong("events"), capacity, confirmed,
                            capacity - confirmed);
                })
                .list();
    }

    // The PostgreSQL driver maps OffsetDateTime to TIMESTAMPTZ; it has no mapping for Instant
    private static OffsetDateTime toTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
