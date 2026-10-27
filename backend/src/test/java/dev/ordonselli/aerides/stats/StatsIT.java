package dev.ordonselli.aerides.stats;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.ordonselli.aerides.support.AbstractIntegrationTest;
import dev.ordonselli.aerides.support.TestGroup;
import dev.ordonselli.aerides.support.TestUser;

/**
 * Runs the statistics SQL against PostgreSQL with a small, fully known data set.
 * <p>
 * The API refuses events in the past, and an event can be completed only after it started. So the
 * events are created in the future through the API, as a real user would, and then moved into the
 * past with SQL: the only shortcut, and it touches nothing the queries don't read anyway.
 */
class StatsIT extends AbstractIntegrationTest {

    // The period asked for: August to October 2026, in Rome time
    private static final String FROM = "2026-07-31T22:00:00Z";
    private static final String TO = "2026-10-31T23:00:00Z";

    @Autowired
    private JdbcClient jdbcClient;

    private TestUser giulia;
    private TestGroup group;

    /*
     * Event A, completed, 31 Aug 22:30 UTC (already 1 September in Rome): Tank 2, Healer 1.
     *   Marco is a Tank, Luca the Healer.
     * Event B, completed, 15 Sep: Tank 1, "healer" 2. Marco is a Tank; Luca signed up and withdrew.
     * Event C, cancelled, 10 Oct: DPS 3.
     * Event D, still published, 20 Oct: Support 5.
     * Everybody joined on 1 January, except Newbie who joined on 10 September (after event A).
     */
    @BeforeEach
    void createData() throws Exception {
        giulia = newUser("Giulia");
        TestUser marco = newUser("Marco");
        TestUser luca = newUser("Luca");
        TestUser newbie = newUser("Newbie");
        group = createGroup(giulia, "Stats test");
        joinGroup(marco, group);
        joinGroup(luca, group);
        joinGroup(newbie, group);
        Instant future = Instant.now().plus(Duration.ofDays(30));

        String eventA = createPublishedEvent(giulia, group, eventJson(future, false, 0, "Tank", 2, "Healer", 1));
        signUp(marco, group, idOf(eventA), slotIdOf(eventA, 0));
        signUp(luca, group, idOf(eventA), slotIdOf(eventA, 1));

        String eventB = createPublishedEvent(giulia, group, eventJson(future, false, 0, "Tank", 1, "healer", 2));
        signUp(marco, group, idOf(eventB), slotIdOf(eventB, 0));
        signUp(luca, group, idOf(eventB), slotIdOf(eventB, 1));
        mockMvc.perform(delete("/api/groups/{g}/events/{e}/signups/me", group.id(), idOf(eventB)).with(as(luca)))
                .andExpect(status().isNoContent());

        String eventC = createPublishedEvent(giulia, group, eventJson(future, false, 0, "DPS", 3));
        mockMvc.perform(post("/api/groups/{g}/events/{e}/cancel", group.id(), idOf(eventC)).with(as(giulia)))
                .andExpect(status().isOk());
        String eventD = createPublishedEvent(giulia, group, eventJson(future, false, 0, "Support", 5));

        moveEvent(idOf(eventA), "2026-08-31T22:30:00Z", "COMPLETED");
        moveEvent(idOf(eventB), "2026-09-15T19:00:00Z", "COMPLETED");
        moveEvent(idOf(eventC), "2026-10-10T19:00:00Z", "CANCELLED");
        moveEvent(idOf(eventD), "2026-10-20T19:00:00Z", "PUBLISHED");
        jdbcClient.sql("UPDATE memberships SET joined_at = :joinedAt WHERE group_id = :groupId")
                .param("joinedAt", timestamp("2026-01-01T00:00:00Z")).param("groupId", group.id()).update();
        jdbcClient.sql("UPDATE memberships SET joined_at = :joinedAt WHERE group_id = :groupId AND user_id = :userId")
                .param("joinedAt", timestamp("2026-09-10T00:00:00Z"))
                .param("groupId", group.id()).param("userId", newbie.id()).update();
    }

    @Test
    void attendanceCountsConfirmedSeatsOnCompletedEventsSinceTheMemberJoined() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/stats", group.id()).with(as(giulia))
                        .param("from", FROM).param("to", TO).param("zone", "Europe/Rome"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendance[*].displayName").value(contains("Giulia", "Luca", "Marco", "Newbie")))
                .andExpect(jsonPath("$.attendance[*].completedEvents").value(contains(2, 2, 2, 1)))
                .andExpect(jsonPath("$.attendance[*].attended").value(contains(0, 1, 2, 0)))
                .andExpect(jsonPath("$.attendance[*].withdrawals").value(contains(0, 1, 0, 0)))
                .andExpect(jsonPath("$.attendance[*].attendanceRate").value(contains(0.0, 0.5, 1.0, 0.0)));
    }

    @Test
    void eventsPerMonthUseTheCallersTimeZone() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/stats", group.id()).with(as(giulia))
                        .param("from", FROM).param("to", TO).param("zone", "Europe/Rome"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsPerMonth[*].month").value(contains("2026-08", "2026-09", "2026-10")))
                .andExpect(jsonPath("$.eventsPerMonth[*].completed").value(contains(0, 2, 0)))
                .andExpect(jsonPath("$.eventsPerMonth[*].cancelled").value(contains(0, 0, 1)));

        // In UTC, event A is still an August event
        mockMvc.perform(get("/api/groups/{id}/stats", group.id()).with(as(giulia))
                        .param("from", "2026-08-01T00:00:00Z").param("to", "2026-11-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventsPerMonth[*].completed").value(contains(1, 1, 0)));
    }

    @Test
    void unfilledRolesComeOnlyFromCompletedEventsAndIgnoreCase() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/stats", group.id()).with(as(giulia))
                        .param("from", FROM).param("to", TO))
                .andExpect(status().isOk())
                // Healer: 1 + 2 seats, only Luca on event A. Tank: 2 + 1 seats, Marco on both events.
                .andExpect(jsonPath("$.unfilledRoles", hasSize(2)))
                .andExpect(jsonPath("$.unfilledRoles[0].roleName", equalToIgnoringCase("healer")))
                .andExpect(jsonPath("$.unfilledRoles[0].events").value(2))
                .andExpect(jsonPath("$.unfilledRoles[0].capacity").value(3))
                .andExpect(jsonPath("$.unfilledRoles[0].confirmed").value(1))
                .andExpect(jsonPath("$.unfilledRoles[0].missing").value(2))
                .andExpect(jsonPath("$.unfilledRoles[1].roleName").value("Tank"))
                .andExpect(jsonPath("$.unfilledRoles[1].missing").value(1));
    }

    @Test
    void invalidPeriodsAreRejected() throws Exception {
        mockMvc.perform(get("/api/groups/{id}/stats", group.id()).with(as(giulia))
                        .param("from", TO).param("to", FROM))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/groups/{id}/stats", group.id()).with(as(giulia)).param("zone", "Not/AZone"))
                .andExpect(status().isBadRequest());
    }

    private void moveEvent(Long eventId, String startsAt, String status) {
        jdbcClient.sql("UPDATE events SET starts_at = :startsAt, status = :status WHERE id = :id")
                .param("startsAt", timestamp(startsAt)).param("status", status).param("id", eventId).update();
    }

    private static OffsetDateTime timestamp(String instant) {
        return Instant.parse(instant).atOffset(ZoneOffset.UTC);
    }
}
