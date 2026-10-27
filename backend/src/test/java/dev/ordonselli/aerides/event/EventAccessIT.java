package dev.ordonselli.aerides.event;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import dev.ordonselli.aerides.game.RawgGame;
import dev.ordonselli.aerides.support.AbstractIntegrationTest;
import dev.ordonselli.aerides.support.TestGroup;
import dev.ordonselli.aerides.support.TestUser;

/**
 * Business rules 1 and 10 for events and sign-ups: only OFFICER and OWNER manage events, and
 * nobody reads or changes events of a group they don't belong to.
 * <p>
 * Same structure as {@code GroupAccessIT}: every endpoint is called by an outsider and by the
 * group's MEMBER, OFFICER and OWNER. 404 for outsiders, 403 for missing roles.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventAccessIT extends AbstractIntegrationTest {

    enum Actor { OUTSIDER, MEMBER, OFFICER, OWNER }

    record Endpoint(String name, Function<EventAccessIT, MockHttpServletRequestBuilder> request, int... expected) {

        int expectedFor(Actor actor) {
            return expected[actor.ordinal()];
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final Instant NEXT_WEEK = Instant.now().plus(Duration.ofDays(7));

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint("GET events", t -> get("/api/groups/{g}/events", t.group.id()), 404, 200, 200, 200),
            new Endpoint("POST event", t -> json(post("/api/groups/{g}/events", t.group.id()),
                    eventJson(NEXT_WEEK, false, 0, "Tank", 1)), 404, 403, 201, 201),
            new Endpoint("GET event", t -> get("/api/groups/{g}/events/{e}", t.group.id(), t.eventId),
                    404, 200, 200, 200),
            new Endpoint("PUT event", t -> json(put("/api/groups/{g}/events/{e}", t.group.id(), t.eventId),
                    t.updateBody()), 404, 403, 200, 200),
            new Endpoint("POST publish", t -> post("/api/groups/{g}/events/{e}/publish", t.group.id(), t.draftId),
                    404, 403, 200, 200),
            new Endpoint("POST cancel", t -> post("/api/groups/{g}/events/{e}/cancel", t.group.id(), t.eventId),
                    404, 403, 200, 200),
            // The event hasn't started yet: officers pass the role check and hit the business rule (422)
            new Endpoint("POST complete", t -> post("/api/groups/{g}/events/{e}/complete", t.group.id(),
                    t.eventId), 404, 403, 422, 422),
            new Endpoint("POST signup", t -> json(post("/api/groups/{g}/events/{e}/signups", t.group.id(),
                    t.eventId), "{\"slotId\": %d}".formatted(t.slotId)), 404, 201, 201, 201),
            new Endpoint("POST approve", t -> post("/api/groups/{g}/events/{e}/signups/{s}/approve", t.group.id(),
                    t.approvalEventId, t.pendingSignupId), 404, 403, 200, 200),
            new Endpoint("POST reject", t -> post("/api/groups/{g}/events/{e}/signups/{s}/reject", t.group.id(),
                    t.approvalEventId, t.pendingSignupId), 404, 403, 200, 200));

    private TestUser outsider;
    private TestUser member;
    private TestUser officer;
    private TestUser owner;
    private TestGroup group;
    private Long eventId;
    private Long slotId;
    private int eventVersion;
    private Long draftId;
    private Long approvalEventId;
    private Long pendingSignupId;

    @BeforeAll
    void createUsers() throws Exception {
        outsider = newUser("Outsider");
        member = newUser("Member");
        officer = newUser("Officer");
        owner = newUser("Owner");
    }

    /** A fresh group and events for every test, because some calls (cancel, sign up...) change them. */
    @BeforeEach
    void createGroupAndEvents() throws Exception {
        group = createGroup(owner, "Event access test");
        joinGroup(member, group);
        joinGroup(officer, group);
        changeRole(owner, group, officer, "OFFICER");

        String event = createPublishedEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 5));
        eventId = idOf(event);
        slotId = ((Number) JsonPath.read(event, "$.slots[0].id")).longValue();
        eventVersion = JsonPath.read(event, "$.version");
        draftId = idOf(createEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 1)));

        String approvalEvent = createPublishedEvent(owner, group, eventJson(NEXT_WEEK, true, 0, "Tank", 1));
        approvalEventId = idOf(approvalEvent);
        TestUser applicant = newUser("Applicant");
        joinGroup(applicant, group);
        pendingSignupId = idOf(mockMvc.perform(json(post("/api/groups/{g}/events/{e}/signups", group.id(),
                        approvalEventId), "{\"slotId\": %d}".formatted(
                        ((Number) JsonPath.read(approvalEvent, "$.slots[0].id")).longValue())).with(as(applicant)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    static Stream<Arguments> accessMatrix() {
        return ENDPOINTS.stream()
                .flatMap(endpoint -> Arrays.stream(Actor.values()).map(actor -> Arguments.of(endpoint, actor)));
    }

    @ParameterizedTest(name = "{0} as {1}")
    @MethodSource("accessMatrix")
    void endpointIsLimitedToTheRightRoles(Endpoint endpoint, Actor actor) throws Exception {
        mockMvc.perform(endpoint.request().apply(this).with(as(user(actor))))
                .andExpect(status().is(endpoint.expectedFor(actor)));
    }

    @Test
    void membersCannotWithdrawWithoutASignupAndOutsidersGetNotFound() throws Exception {
        mockMvc.perform(delete("/api/groups/{g}/events/{e}/signups/me", group.id(), eventId).with(as(member)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/groups/{g}/events/{e}/signups/me", group.id(), eventId).with(as(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anEventCannotBeReachedThroughAnotherGroupsUrl() throws Exception {
        // The outsider owns another group, and uses its URL with this group's event id
        TestGroup otherGroup = createGroup(outsider, "Other group");

        mockMvc.perform(get("/api/groups/{g}/events/{e}", otherGroup.id(), eventId).with(as(outsider)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/groups/{g}/events/{e}/cancel", otherGroup.id(), eventId).with(as(outsider)))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(post("/api/groups/{g}/events/{e}/signups", otherGroup.id(), eventId),
                        "{\"slotId\": %d}".formatted(slotId)).with(as(outsider)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/groups/{g}/events/{e}/signups/{s}/approve", otherGroup.id(), approvalEventId,
                        pendingSignupId).with(as(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aSlotOfAnotherEventCannotBeUsed() throws Exception {
        String other = createPublishedEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Healer", 1));
        Long otherSlotId = ((Number) JsonPath.read(other, "$.slots[0].id")).longValue();

        mockMvc.perform(json(post("/api/groups/{g}/events/{e}/signups", group.id(), eventId),
                        "{\"slotId\": %d}".formatted(otherSlotId)).with(as(member)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aGameOfAnotherGroupCannotBeAttachedToAnEvent() throws Exception {
        when(rawgClient.getGame(anyLong())).thenReturn(new RawgGame(7L, "Game", "game", null, null));
        TestGroup otherGroup = createGroup(outsider, "Other group");
        Long foreignGameId = idOf(mockMvc.perform(json(post("/api/groups/{g}/games", otherGroup.id()),
                        "{\"rawgId\": 7}").with(as(outsider)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        String body = eventJson(NEXT_WEEK, false, 0, "Tank", 1)
                .replace("\"title\"", "\"gameId\": %d, \"title\"".formatted(foreignGameId));
        mockMvc.perform(json(post("/api/groups/{g}/events", group.id()), body).with(as(owner)))
                .andExpect(status().isNotFound());
    }

    private String updateBody() {
        return """
                {"title": "Renamed", "startsAt": "%s", "durationMinutes": 90, "requiresApproval": false,
                 "withdrawDeadlineHours": 0, "slots": [{"id": %d, "roleName": "Tank", "capacity": 5}],
                 "version": %d}
                """.formatted(NEXT_WEEK, slotId, eventVersion);
    }

    private TestUser user(Actor actor) {
        return switch (actor) {
            case OUTSIDER -> outsider;
            case MEMBER -> member;
            case OFFICER -> officer;
            case OWNER -> owner;
        };
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
