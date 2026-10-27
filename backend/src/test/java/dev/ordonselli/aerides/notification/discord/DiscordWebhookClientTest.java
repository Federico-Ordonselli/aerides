package dev.ordonselli.aerides.notification.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClientException;

class DiscordWebhookClientTest {

    private static final String WEBHOOK_PATH = "/api/webhooks/123/secret-token";

    @RegisterExtension
    static WireMockExtension discord = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private final DiscordWebhookClient client =
            new DiscordWebhookClient(new DiscordProperties(Duration.ofSeconds(1), Duration.ofMillis(500)));

    @Test
    void postsTheContentWithMentionsDisabled() {
        discord.stubFor(post(urlEqualTo(WEBHOOK_PATH)).willReturn(noContent()));

        client.post(discord.baseUrl() + WEBHOOK_PATH, "New event: @everyone");

        discord.verify(postRequestedFor(urlEqualTo(WEBHOOK_PATH)).withRequestBody(equalToJson("""
                {"content": "New event: @everyone", "allowed_mentions": {"parse": []}}
                """)));
    }

    @Test
    void aServerErrorIsReportedToTheCaller() {
        discord.stubFor(post(urlEqualTo(WEBHOOK_PATH)).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.post(discord.baseUrl() + WEBHOOK_PATH, "Hello"))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void aSlowServerIsCutOffByTheReadTimeout() {
        discord.stubFor(post(urlEqualTo(WEBHOOK_PATH)).willReturn(noContent().withFixedDelay(2_000)));

        assertThatThrownBy(() -> client.post(discord.baseUrl() + WEBHOOK_PATH, "Hello"))
                .isInstanceOf(RestClientException.class);
    }
}
