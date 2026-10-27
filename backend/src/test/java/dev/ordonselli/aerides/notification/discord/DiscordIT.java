package dev.ordonselli.aerides.notification.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;

import dev.ordonselli.aerides.group.Group;
import dev.ordonselli.aerides.group.GroupRepository;
import dev.ordonselli.aerides.support.AbstractIntegrationTest;
import dev.ordonselli.aerides.support.TestGroup;
import dev.ordonselli.aerides.support.TestUser;

/** Discord announcements (business rule 9), with WireMock in place of Discord. */
class DiscordIT extends AbstractIntegrationTest {

    private static final Instant NEXT_WEEK = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(5);

    @RegisterExtension
    static WireMockExtension discord = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Autowired
    private GroupRepository groupRepository;

    private TestUser owner;
    private TestGroup group;
    private String webhookPath;

    @BeforeEach
    void setUp() throws Exception {
        owner = newUser("Owner");
        group = createGroup(owner, "Discord guild");
        webhookPath = "/api/webhooks/" + group.id() + "/token";
        // Saved directly: the API accepts only https://discord.com URLs, WireMock runs on http://localhost
        Group entity = groupRepository.findById(group.id()).orElseThrow();
        entity.setDiscordWebhookUrl(discord.baseUrl() + webhookPath);
        groupRepository.save(entity);
        discord.stubFor(post(urlEqualTo(webhookPath)).willReturn(noContent()));
    }

    @Test
    void publishingAnEventIsAnnounced() throws Exception {
        createPublishedEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 2));

        await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> discord.verify(1, postRequestedFor(urlEqualTo(webhookPath))
                .withRequestBody(matchingJsonPath("$.content", containing("Nuovo evento: **Raid night**")))
                .withRequestBody(matchingJsonPath("$.content", containing("<t:" + NEXT_WEEK.getEpochSecond() + ":F>")))));
    }

    @Test
    void theSignupThatLeavesOneSeatIsAnnounced() throws Exception {
        String event = createPublishedEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 2, "Healer", 1));
        TestUser member = newUser("Member");
        joinGroup(member, group);

        signUp(owner, group, idOf(event), slotIdOf(event, 0)); // 2 seats left
        signUp(member, group, idOf(event), slotIdOf(event, 1)); // 1 seat left

        await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> discord.verify(1, postRequestedFor(urlEqualTo(webhookPath))
                .withRequestBody(matchingJsonPath("$.content", containing("Resta un solo posto")))));
    }

    @Test
    void aFailingWebhookDoesNotBreakPublishing() throws Exception {
        discord.stubFor(post(urlEqualTo(webhookPath)).willReturn(aResponse().withStatus(500)));

        Long eventId = idOf(createPublishedEvent(owner, group, eventJson(NEXT_WEEK, false, 0, "Tank", 2)));

        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                discord.verify(1, postRequestedFor(urlEqualTo(webhookPath))));
        mockMvc.perform(get("/api/groups/{g}/events/{e}", group.id(), eventId).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }
}
