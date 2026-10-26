package dev.ordonselli.raidboard.game;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.ordonselli.raidboard.common.error.ExternalServiceUnavailableException;
import dev.ordonselli.raidboard.common.error.NotFoundException;

/** Tests the RAWG client against WireMock, a local fake HTTP server: no real network calls. */
class RawgClientTest {

    private static final String API_KEY = "test-api-key";

    @RegisterExtension
    static WireMockExtension rawg = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private RawgClient client(String apiKey) {
        return new RawgClient(new RawgProperties(rawg.baseUrl(), apiKey, Duration.ofSeconds(1),
                Duration.ofMillis(500)));
    }

    @Test
    void searchSendsKeyAndQueryAndMapsTheResults() {
        rawg.stubFor(get(urlPathEqualTo("/games")).willReturn(okJson("""
                {"count": 1, "results": [{
                  "id": 3328, "name": "The Witcher 3: Wild Hunt", "slug": "the-witcher-3-wild-hunt",
                  "background_image": "https://media.rawg.io/witcher3.jpg", "released": "2015-05-18",
                  "rating": 4.66
                }]}
                """)));

        List<RawgGame> games = client(API_KEY).search("  witcher ");

        assertThat(games).containsExactly(new RawgGame(3328, "The Witcher 3: Wild Hunt", "the-witcher-3-wild-hunt",
                "https://media.rawg.io/witcher3.jpg", "2015-05-18"));
        rawg.verify(getRequestedFor(urlPathEqualTo("/games"))
                .withQueryParam("key", equalTo(API_KEY))
                .withQueryParam("search", equalTo("witcher")));
    }

    @Test
    void serverErrorBecomesServiceUnavailable() {
        rawg.stubFor(get(urlPathEqualTo("/games")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client(API_KEY).search("witcher"))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                // The message goes to the client and to the logs: it must never contain the API key
                .message().doesNotContain(API_KEY);
    }

    @Test
    void slowResponseTimesOutAndBecomesServiceUnavailable() {
        // RAWG answers after 2 seconds, the client gives up after 500 ms
        rawg.stubFor(get(urlPathEqualTo("/games")).willReturn(okJson("{\"results\": []}").withFixedDelay(2_000)));

        long start = System.nanoTime();
        assertThatThrownBy(() -> client(API_KEY).search("witcher"))
                .isInstanceOf(ExternalServiceUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(1_800));
    }

    @Test
    void withoutApiKeyNoRequestIsSent() {
        assertThatThrownBy(() -> client("").search("witcher"))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("The game catalog is not configured");
        rawg.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void unknownGameBecomesNotFound() {
        rawg.stubFor(get(urlPathEqualTo("/games/999999")).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client(API_KEY).getGame(999_999)).isInstanceOf(NotFoundException.class);
    }
}
