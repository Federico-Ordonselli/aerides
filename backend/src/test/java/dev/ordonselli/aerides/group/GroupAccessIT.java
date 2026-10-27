package dev.ordonselli.aerides.group;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Business rule 10: users must never read or modify data of groups they don't belong to,
 * and inside a group each endpoint is limited to the right roles.
 * <p>
 * Every endpoint is called by four users: an outsider and the group's MEMBER, OFFICER and OWNER.
 * Expected answers: 404 for outsiders (the group's existence is not revealed), 403 for members
 * without the required role, success for the others.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // lets @BeforeAll use instance fields: users are created once
class GroupAccessIT extends AbstractIntegrationTest {

    enum Actor { OUTSIDER, MEMBER, OFFICER, OWNER }

    /**
     * One row of the access matrix: how to call the endpoint, and the expected HTTP status
     * for outsider, member, officer and owner, in this order.
     */
    record Endpoint(String name, Function<GroupAccessIT, MockHttpServletRequestBuilder> request, int... expected) {

        int expectedFor(Actor actor) {
            return expected[actor.ordinal()];
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint("GET group", t -> get("/api/groups/{id}", t.group.id()), 404, 200, 200, 200),
            new Endpoint("PATCH group", t -> json(patch("/api/groups/{id}", t.group.id()), "{\"name\": \"Renamed\"}"),
                    404, 403, 200, 200),
            new Endpoint("DELETE group", t -> delete("/api/groups/{id}", t.group.id()), 404, 403, 403, 204),
            new Endpoint("POST invite-code", t -> post("/api/groups/{id}/invite-code", t.group.id()),
                    404, 403, 403, 200),
            new Endpoint("PUT discord-webhook", t -> json(put("/api/groups/{id}/discord-webhook", t.group.id()),
                    "{\"url\": \"https://discord.com/api/webhooks/1/abc\"}"), 404, 403, 403, 204),
            new Endpoint("GET members", t -> get("/api/groups/{id}/members", t.group.id()), 404, 200, 200, 200),
            new Endpoint("PATCH member role", t -> json(patch("/api/groups/{id}/members/{userId}", t.group.id(),
                    t.member.id()), "{\"role\": \"OFFICER\"}"), 404, 403, 403, 200),
            new Endpoint("DELETE member", t -> delete("/api/groups/{id}/members/{userId}", t.group.id(),
                    t.member.id()), 404, 403, 403, 204),
            // The owner may not leave (422 business rule), everyone else in the group may
            new Endpoint("DELETE members/me (leave)", t -> delete("/api/groups/{id}/members/me", t.group.id()),
                    404, 204, 204, 422),
            new Endpoint("GET games", t -> get("/api/groups/{id}/games", t.group.id()), 404, 200, 200, 200),
            new Endpoint("POST game", t -> json(post("/api/groups/{id}/games", t.group.id()), "{\"rawgId\": 2}"),
                    404, 403, 201, 201),
            new Endpoint("DELETE game", t -> delete("/api/groups/{id}/games/{gameId}", t.group.id(), t.gameId),
                    404, 403, 204, 204),
            new Endpoint("GET stats", t -> get("/api/groups/{id}/stats", t.group.id()), 404, 200, 200, 200),
            new Endpoint("GET audit", t -> get("/api/groups/{id}/audit", t.group.id()), 404, 403, 200, 200));

    private TestUser outsider;
    private TestUser member;
    private TestUser officer;
    private TestUser owner;
    private TestGroup group;
    private Long gameId;

    @BeforeAll
    void createUsers() throws Exception {
        outsider = newUser("Outsider");
        member = newUser("Member");
        officer = newUser("Officer");
        owner = newUser("Owner");
    }

    /** A fresh group for every test, because some calls (delete, leave...) change it. */
    @BeforeEach
    void createGroup() throws Exception {
        when(rawgClient.getGame(anyLong())).thenAnswer(invocation -> {
            long rawgId = invocation.getArgument(0);
            return new RawgGame(rawgId, "Game " + rawgId, "game-" + rawgId, null, null);
        });
        group = createGroup(owner, "Access test");
        joinGroup(member, group);
        joinGroup(officer, group);
        changeRole(owner, group, officer, "OFFICER");
        String game = mockMvc.perform(json(post("/api/groups/{id}/games", group.id()), "{\"rawgId\": 1}")
                        .with(as(owner)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        gameId = ((Integer) JsonPath.read(game, "$.id")).longValue();
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
    void aGroupThatDoesNotExistLooksTheSameAsOneYouDoNotBelongTo() throws Exception {
        mockMvc.perform(get("/api/groups/{id}", 999_999).with(as(owner))).andExpect(status().isNotFound());
    }

    @Test
    void aGameCannotBeDeletedThroughAnotherGroupsUrl() throws Exception {
        // The owner of group B tries to delete group A's game using group B's URL
        TestGroup otherGroup = createGroup(outsider, "Other group");

        mockMvc.perform(delete("/api/groups/{id}/games/{gameId}", otherGroup.id(), gameId).with(as(outsider)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/groups/{id}/games", group.id()).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getContentAsString()).contains("\"id\":" + gameId));
    }

    @Test
    void aMemberOfAnotherGroupCannotBeTargetedThroughThisGroup() throws Exception {
        // The outsider is not in this group: changing their role here must not find them
        mockMvc.perform(json(patch("/api/groups/{id}/members/{userId}", group.id(), outsider.id()),
                        "{\"role\": \"OFFICER\"}").with(as(owner)))
                .andExpect(status().isNotFound());
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
