package dev.ordonselli.raidboard.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import dev.ordonselli.raidboard.audit.AuditAction;
import dev.ordonselli.raidboard.audit.AuditEntry;
import dev.ordonselli.raidboard.audit.AuditEntryRepository;
import dev.ordonselli.raidboard.support.AbstractIntegrationTest;
import dev.ordonselli.raidboard.support.TestGroup;
import dev.ordonselli.raidboard.support.TestUser;

/** Events and sign-ups end to end, through the HTTP API and a real PostgreSQL. */
class EventIT extends AbstractIntegrationTest {

    // Whole seconds: PostgreSQL stores microseconds, so a nanosecond Instant would not compare equal
    private static final Instant NEXT_WEEK = Instant.now().plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private AuditEntryRepository auditEntryRepository;

    private TestUser owner;
    private TestUser alice;
    private TestUser bob;
    private TestGroup group;

    @BeforeEach
    void setUp() throws Exception {
        owner = newUser("Owner");
        alice = newUser("Alice");
        bob = newUser("Bob");
        group = createGroup(owner, "Event test");
        joinGroup(alice, group);
        joinGroup(bob, group);
    }

    @Test
    void aDraftIsHiddenFromMembersUntilItIsPublished() throws Exception {
        Long eventId = idOf(createEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 2)));

        mockMvc.perform(get("/api/groups/{g}/events/{e}", group.id(), eventId).with(as(alice)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/groups/{g}/events", group.id()).with(as(alice)))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(post("/api/groups/{g}/events/{e}/publish", group.id(), eventId).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/groups/{g}/events/{e}", group.id(), eventId).with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].roleName").value("Tank"))
                .andExpect(jsonPath("$.slots[0].capacity").value(2));
        assertThat(auditEntryRepository.findAllByGroupIdOrderByIdAsc(group.id()))
                .extracting(AuditEntry::getAction)
                .contains(AuditAction.EVENT_CREATED, AuditAction.EVENT_PUBLISHED);
    }

    @Test
    void aWeeklySeriesKeepsTheLocalTimeAcrossTheDaylightSavingChange() throws Exception {
        // Thursday 18 March 2027, 21:00 in Rome (UTC+1). Summer time starts on 28 March (UTC+2).
        String body = """
                {"title": "Weekly raid", "startsAt": "2027-03-18T20:00:00Z", "durationMinutes": 120,
                 "requiresApproval": false, "withdrawDeadlineHours": 0, "slots": [{"roleName": "DPS", "capacity": 5}],
                 "weeklyOccurrences": 3, "timeZone": "Europe/Rome"}
                """;
        String first = createEvent(owner, group, body);
        assertThat((String) JsonPath.read(first, "$.recurrenceRule")).isEqualTo("FREQ=WEEKLY;COUNT=3");

        String page = mockMvc.perform(get("/api/groups/{g}/events", group.id()).with(as(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> starts = JsonPath.read(page, "$.content[*].startsAt");
        List<String> seriesIds = JsonPath.read(page, "$.content[*].seriesId");
        // 21:00 in Rome every week: 20:00Z in winter, 19:00Z after the switch
        assertThat(starts).containsExactly("2027-03-18T20:00:00Z", "2027-03-25T20:00:00Z", "2027-04-01T19:00:00Z");
        assertThat(seriesIds).hasSize(3).doesNotContainNull().containsOnly(seriesIds.getFirst());
    }

    @Test
    void aWithdrawalPromotesTheFirstWaitlistedUser() throws Exception {
        String event = createPublishedEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Healer", 1));
        Long eventId = idOf(event);
        Long slotId = slotId(event);

        signUp(alice, eventId, slotId).andExpect(jsonPath("$.status").value("CONFIRMED"));
        signUp(bob, eventId, slotId).andExpect(jsonPath("$.status").value("WAITLISTED"));

        mockMvc.perform(delete("/api/groups/{g}/events/{e}/signups/me", group.id(), eventId).with(as(alice)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/groups/{g}/events/{e}", group.id(), eventId).with(as(bob)))
                .andExpect(jsonPath("$.slots[0].confirmed", hasSize(1)))
                .andExpect(jsonPath("$.slots[0].confirmed[0].displayName").value("Bob"))
                .andExpect(jsonPath("$.slots[0].waitlisted", hasSize(0)))
                .andExpect(jsonPath("$.mySignup.status").value("CONFIRMED"));
    }

    @Test
    void aSecondActiveSignupForTheSameEventIsRejected() throws Exception {
        String event = createPublishedEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 2, "Healer", 2));
        Long eventId = idOf(event);
        List<Integer> slotIds = JsonPath.read(event, "$.slots[*].id");

        signUp(alice, eventId, slotIds.get(0).longValue()).andExpect(status().isCreated());
        signUp(alice, eventId, slotIds.get(1).longValue()).andExpect(status().isConflict());
    }

    @Test
    void confirmedUsersCannotWithdrawAfterTheDeadline() throws Exception {
        // Starts in one hour, withdrawals close 24 hours before: the deadline has already passed
        String event = createPublishedEvent(owner, group,
                eventJson(Instant.now().plus(Duration.ofHours(1)), false, 24, "Tank", 1));
        Long eventId = idOf(event);
        signUp(alice, eventId, slotId(event)).andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(delete("/api/groups/{g}/events/{e}/signups/me", group.id(), eventId).with(as(alice)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void withApprovalSignupsWaitForAnOfficer() throws Exception {
        String event = createPublishedEvent(owner, group, eventJson(NEXT_WEEK, true, 0, "Tank", 1));
        Long eventId = idOf(event);
        Long slotId = slotId(event);

        Long aliceSignup = idOf(signUp(alice, eventId, slotId)
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString());
        Long bobSignup = idOf(signUp(bob, eventId, slotId).andReturn().getResponse().getContentAsString());

        approve(aliceSignup, eventId).andExpect(jsonPath("$.status").value("CONFIRMED"));
        // The only seat is taken: approving Bob puts him on the waitlist
        approve(bobSignup, eventId).andExpect(jsonPath("$.status").value("WAITLISTED"));
        approve(bobSignup, eventId).andExpect(status().isConflict());
    }

    @Test
    void moreCapacityPromotesTheWaitlist() throws Exception {
        String event = createPublishedEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 1));
        Long eventId = idOf(event);
        Long slotId = slotId(event);
        signUp(alice, eventId, slotId);
        signUp(bob, eventId, slotId).andExpect(jsonPath("$.status").value("WAITLISTED"));

        mockMvc.perform(put("/api/groups/{g}/events/{e}", group.id(), eventId).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(slotId, 2, JsonPath.read(event, "$.version"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].capacity").value(2))
                .andExpect(jsonPath("$.slots[0].confirmed", hasSize(2)))
                .andExpect(jsonPath("$.slots[0].waitlisted", hasSize(0)));
    }

    @Test
    void capacityCannotGoBelowTheConfirmedParticipants() throws Exception {
        String event = createPublishedEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 2));
        Long eventId = idOf(event);
        Long slotId = slotId(event);
        signUp(alice, eventId, slotId);
        signUp(bob, eventId, slotId);

        mockMvc.perform(put("/api/groups/{g}/events/{e}", group.id(), eventId).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(slotId, 1, JsonPath.read(event, "$.version"))))
                .andExpect(status().isConflict());
    }

    @Test
    void anUpdateBasedOnAnOldVersionIsRejected() throws Exception {
        String event = createPublishedEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 2));
        Long eventId = idOf(event);
        Long slotId = slotId(event);
        int loadedVersion = JsonPath.read(event, "$.version");

        // First save wins and returns the new version
        String saved = mockMvc.perform(put("/api/groups/{g}/events/{e}", group.id(), eventId).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(slotId, 3, loadedVersion)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(saved, "$.version")).isGreaterThan(loadedVersion);

        // A second save based on what was loaded before is stale
        mockMvc.perform(put("/api/groups/{g}/events/{e}", group.id(), eventId).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(slotId, 4, loadedVersion)))
                .andExpect(status().isConflict());
    }

    @Test
    void theListIsFilteredPaginatedAndSorted() throws Exception {
        createPublishedEvent(owner, group, eventJson(NEXT_WEEK.plus(Duration.ofDays(2)), false, 0, "Tank", 1));
        createPublishedEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 1));
        createEvent(owner, group, eventJson(NEXT_WEEK.plus(Duration.ofDays(1)), false, 0, "Tank", 1));

        mockMvc.perform(get("/api/groups/{g}/events", group.id()).with(as(owner))
                        .param("status", "PUBLISHED").param("size", "1").param("sort", "startsAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].startsAt").value(NEXT_WEEK.plus(Duration.ofDays(2)).toString()));

        mockMvc.perform(get("/api/groups/{g}/events", group.id()).with(as(owner))
                        .param("from", NEXT_WEEK.plus(Duration.ofHours(1)).toString()))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/groups/{g}/events", group.id()).with(as(owner)).param("sort", "group.name"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidEventsAreRejected() throws Exception {
        // In the past
        mockMvc.perform(post("/api/groups/{g}/events", group.id()).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(Instant.now().minus(Duration.ofDays(1)), false, 0, "Tank", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startsAt").exists());
        // Capacity 0
        mockMvc.perform(post("/api/groups/{g}/events", group.id()).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(NEXT_WEEK, false, 0, "Tank", 0)))
                .andExpect(status().isBadRequest());
        // Same role twice
        mockMvc.perform(post("/api/groups/{g}/events", group.id()).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(NEXT_WEEK, false, 0, "Tank", 1, "tank ", 1)))
                .andExpect(status().isBadRequest());
    }

    private ResultActions signUp(TestUser user, Long eventId, Long slotId)
            throws Exception {
        return mockMvc.perform(post("/api/groups/{g}/events/{e}/signups", group.id(), eventId).with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\": %d}".formatted(slotId)));
    }

    private ResultActions approve(Long signupId, Long eventId)
            throws Exception {
        return mockMvc.perform(post("/api/groups/{g}/events/{e}/signups/{s}/approve", group.id(), eventId, signupId)
                .with(as(owner)));
    }

    private static Long slotId(String eventJson) {
        return ((Number) JsonPath.read(eventJson, "$.slots[0].id")).longValue();
    }

    private static String updateJson(Long slotId, int capacity, int version) {
        return """
                {"title": "Raid night", "startsAt": "%s", "durationMinutes": 120, "requiresApproval": false,
                 "withdrawDeadlineHours": 0, "slots": [{"id": %d, "roleName": "Tank", "capacity": %d}], "version": %d}
                """.formatted(NEXT_WEEK, slotId, capacity, version);
    }
}
