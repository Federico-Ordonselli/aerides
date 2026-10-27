package dev.ordonselli.aerides.notification.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Posts messages to a Discord channel through an incoming webhook
 * (https://discord.com/developers/docs/resources/webhook#execute-webhook).
 * <p>
 * Short timeouts: Discord being slow must not keep our threads busy. Errors are thrown as
 * {@link org.springframework.web.client.RestClientException}; the caller decides what to do.
 */
@Component
@EnableConfigurationProperties(DiscordProperties.class)
public class DiscordWebhookClient {

    private final RestClient restClient;

    public DiscordWebhookClient(DiscordProperties properties) {
        // HTTP/1.1: the JDK client otherwise tries an HTTP/2 upgrade on plain http, which loses POST bodies
        // with some servers. Discord itself is https and webhooks are single small requests: nothing is lost.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public void post(String webhookUrl, String content) {
        restClient.post()
                .uri(URI.create(webhookUrl))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new WebhookMessage(content, new AllowedMentions(List.of())))
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * {@code allowed_mentions} with an empty list: an event titled "@everyone" must not ping the whole
     * server. Event titles are written by users, so the message is treated as untrusted text.
     */
    record WebhookMessage(String content, @JsonProperty("allowed_mentions") AllowedMentions allowedMentions) {
    }

    record AllowedMentions(List<String> parse) {
    }
}
