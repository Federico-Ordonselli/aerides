package dev.ordonselli.aerides.group;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code url = null} removes the webhook.
 * <p>
 * Only real Discord webhook URLs are accepted. The server will later send requests to this URL, so
 * accepting any address would let a user make our server call internal services (SSRF).
 */
public record DiscordWebhookRequest(
        @Size(max = 500)
        @Pattern(regexp = "https://(discord\\.com|discordapp\\.com)/api/webhooks/\\d+/[A-Za-z0-9_-]+",
                message = "must be a Discord webhook URL (https://discord.com/api/webhooks/...)")
        String url) {
}
