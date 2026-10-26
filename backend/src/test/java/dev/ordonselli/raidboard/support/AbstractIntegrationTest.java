package dev.ordonselli.raidboard.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import dev.ordonselli.raidboard.game.RawgClient;

/**
 * Base class for integration tests: full Spring context, real PostgreSQL (Testcontainers) with the
 * Flyway migrations applied, and MockMvc to call the API through the whole security filter chain.
 * <p>
 * All subclasses share the same configuration, so Spring reuses one context and one container.
 * For the same reason the RAWG client mock is declared here, not in the single test classes:
 * a different set of mocks would make Spring start a new context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    /** No real calls to RAWG in integration tests. Reset automatically after each test. */
    @MockitoBean
    protected RawgClient rawgClient;

    /** Registers a new user with a unique email and returns it with its access token. */
    protected TestUser newUser(String displayName) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "supersecret", "displayName": "%s"}
                                """.formatted(email, displayName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.user.id");
        String token = JsonPath.read(body, "$.accessToken");
        return new TestUser(id.longValue(), email, token);
    }

    /** Adds the user's bearer token to a MockMvc request: {@code get(...).with(as(user))}. */
    protected static RequestPostProcessor as(TestUser user) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + user.accessToken());
            return request;
        };
    }

    /** Creates a group owned by {@code owner}. */
    protected TestGroup createGroup(TestUser owner, String name) throws Exception {
        String body = mockMvc.perform(post("/api/groups").with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.id");
        String inviteCode = JsonPath.read(body, "$.inviteCode");
        return new TestGroup(id.longValue(), inviteCode);
    }

    protected void joinGroup(TestUser user, TestGroup group) throws Exception {
        mockMvc.perform(post("/api/groups/join").with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\": \"%s\"}".formatted(group.inviteCode())))
                .andExpect(status().isOk());
    }

    protected void changeRole(TestUser owner, TestGroup group, TestUser target, String role) throws Exception {
        mockMvc.perform(patch("/api/groups/{groupId}/members/{userId}", group.id(), target.id()).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"%s\"}".formatted(role)))
                .andExpect(status().isOk());
    }
}
