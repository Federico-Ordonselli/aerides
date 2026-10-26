package dev.ordonselli.raidboard.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.icegreen.greenmail.util.GreenMail;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
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
 * The server also listens on a random port, for the WebSocket tests, and emails go to an in-memory
 * SMTP server ({@link TestMailConfiguration}).
 * <p>
 * All subclasses share the same configuration, so Spring reuses one context and one container.
 * For the same reason the RAWG client mock is declared here, not in the single test classes:
 * a different set of mocks would make Spring start a new context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected GreenMail greenMail;

    /** No real calls to RAWG in integration tests. Reset automatically after each test. */
    @MockitoBean
    protected RawgClient rawgClient;

    /** Registers a new user with a unique email and returns it with its access token. */
    protected TestUser newUser(String displayName) throws Exception {
        return newUser(displayName, "it");
    }

    protected TestUser newUser(String displayName, String locale) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "supersecret", "displayName": "%s", "locale": "%s"}
                                """.formatted(email, displayName, locale)))
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

    /**
     * Creates a DRAFT event through the API with the given JSON body and returns the response body.
     * See {@link #eventJson} for a ready-made body.
     */
    protected String createEvent(TestUser officer, TestGroup group, String body) throws Exception {
        return mockMvc.perform(post("/api/groups/{groupId}/events", group.id()).with(as(officer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    /** Creates and publishes an event; returns the published event's JSON. */
    protected String createPublishedEvent(TestUser officer, TestGroup group, String body) throws Exception {
        Long eventId = idOf(createEvent(officer, group, body));
        return mockMvc.perform(post("/api/groups/{groupId}/events/{eventId}/publish", group.id(), eventId)
                        .with(as(officer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** A valid event body starting at {@code startsAt}, with the given slots, e.g. {@code "Tank", 2, "Healer", 1}. */
    protected static String eventJson(Instant startsAt, boolean requiresApproval, int withdrawDeadlineHours,
            Object... roleAndCapacity) {
        StringBuilder slots = new StringBuilder();
        for (int i = 0; i < roleAndCapacity.length; i += 2) {
            if (!slots.isEmpty()) {
                slots.append(", ");
            }
            slots.append("{\"roleName\": \"%s\", \"capacity\": %d}".formatted(roleAndCapacity[i],
                    roleAndCapacity[i + 1]));
        }
        return """
                {"title": "Raid night", "startsAt": "%s", "durationMinutes": 120,
                 "requiresApproval": %s, "withdrawDeadlineHours": %d, "slots": [%s]}
                """.formatted(startsAt, requiresApproval, withdrawDeadlineHours, slots);
    }

    /** Emails received so far by one address. Every test user has a unique address, so tests don't see each other's mail. */
    protected List<MimeMessage> emailsTo(TestUser user) {
        return Arrays.stream(greenMail.getReceivedMessages())
                .filter(message -> {
                    try {
                        return Arrays.stream(message.getAllRecipients())
                                .anyMatch(address -> address.toString().equals(user.email()));
                    } catch (MessagingException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }

    protected Long signUp(TestUser user, TestGroup group, Long eventId, Long slotId) throws Exception {
        String body = mockMvc.perform(post("/api/groups/{groupId}/events/{eventId}/signups", group.id(), eventId)
                        .with(as(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\": %d}".formatted(slotId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(body);
    }

    protected static Long slotIdOf(String eventJson, int index) {
        return ((Number) JsonPath.read(eventJson, "$.slots[%d].id".formatted(index))).longValue();
    }

    protected static Long idOf(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    protected void changeRole(TestUser owner, TestGroup group, TestUser target, String role) throws Exception {
        mockMvc.perform(patch("/api/groups/{groupId}/members/{userId}", group.id(), target.id()).with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"%s\"}".formatted(role)))
                .andExpect(status().isOk());
    }
}
