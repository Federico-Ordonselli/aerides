package dev.ordonselli.aerides.game;

import java.net.http.HttpClient;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import dev.ordonselli.aerides.common.error.ExternalServiceUnavailableException;
import dev.ordonselli.aerides.common.error.NotFoundException;

/**
 * Client for the RAWG video game database (https://rawg.io/apidocs).
 * <p>
 * Calls happen on the server, so the API key never reaches the browser. Timeouts are short:
 * a slow RAWG must not keep our request threads busy. Every failure becomes an
 * {@link ExternalServiceUnavailableException} (503), so the rest of the application keeps working.
 */
@Component
@EnableConfigurationProperties(RawgProperties.class)
public class RawgClient {

    private static final Logger log = LoggerFactory.getLogger(RawgClient.class);
    private static final int SEARCH_PAGE_SIZE = 10;

    private final RawgProperties properties;
    private final RestClient restClient;

    public RawgClient(RawgProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /** Cached for a few minutes (see application.yml): RAWG's free plan has a monthly request quota. */
    @Cacheable(cacheNames = "rawg-search", key = "#query.trim().toLowerCase()")
    public List<RawgGame> search(String query) {
        requireApiKey();
        try {
            RawgSearchResponse response = restClient.get()
                    .uri(uri -> uri.path("/games")
                            .queryParam("key", properties.apiKey())
                            .queryParam("search", query.trim())
                            .queryParam("page_size", SEARCH_PAGE_SIZE)
                            .build())
                    .retrieve()
                    .body(RawgSearchResponse.class);
            return response == null || response.results() == null ? List.of() : response.results();
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    public RawgGame getGame(long rawgId) {
        requireApiKey();
        try {
            RawgGame game = restClient.get()
                    .uri(uri -> uri.path("/games/{id}").queryParam("key", properties.apiKey()).build(rawgId))
                    .retrieve()
                    .body(RawgGame.class);
            if (game == null) {
                throw new NotFoundException("Game not found in the catalog");
            }
            return game;
        } catch (HttpClientErrorException.NotFound e) {
            throw new NotFoundException("Game not found in the catalog");
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new ExternalServiceUnavailableException("The game catalog is not configured");
        }
    }

    /**
     * Logs only the error type, never e.getMessage(): Spring includes the request URL in the message,
     * and the URL contains the API key. For the same reason the original exception is not attached.
     */
    private static ExternalServiceUnavailableException unavailable(RestClientException e) {
        log.warn("RAWG request failed: {}", e.getClass().getSimpleName());
        return new ExternalServiceUnavailableException("The game catalog is unavailable, try again later");
    }
}
