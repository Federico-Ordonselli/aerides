package dev.ordonselli.aerides.notification.discord;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.ordonselli.aerides.event.Event;
import dev.ordonselli.aerides.event.EventAlmostFullEvent;
import dev.ordonselli.aerides.event.EventPublishedEvent;
import dev.ordonselli.aerides.event.EventRepository;
import dev.ordonselli.aerides.notification.NotificationProperties;

/**
 * Announces events on the group's Discord channel, if the group configured a webhook (business rule 9):
 * when an event is published and when a sign-up leaves one free seat.
 * <p>
 * Runs after the commit and in the background. Any failure is logged and swallowed: Discord being down
 * must never break publishing or signing up.
 */
@Component
public class DiscordNotifier {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotifier.class);

    private final EventRepository eventRepository;
    private final DiscordWebhookClient webhookClient;
    private final MessageSource messageSource;
    private final NotificationProperties notificationProperties;

    public DiscordNotifier(EventRepository eventRepository, DiscordWebhookClient webhookClient,
            MessageSource messageSource, NotificationProperties notificationProperties) {
        this.eventRepository = eventRepository;
        this.webhookClient = webhookClient;
        this.messageSource = messageSource;
        this.notificationProperties = notificationProperties;
    }

    @Async
    @TransactionalEventListener
    public void onPublished(EventPublishedEvent event) {
        announce(event.eventId(), "discord.event-published");
    }

    @Async
    @TransactionalEventListener
    public void onAlmostFull(EventAlmostFullEvent event) {
        announce(event.eventId(), "discord.event-almost-full");
    }

    private void announce(Long eventId, String messageKey) {
        try {
            Event event = eventRepository.findWithGroupById(eventId).orElse(null);
            if (event == null || event.getGroup().getDiscordWebhookUrl() == null) {
                return;
            }
            webhookClient.post(event.getGroup().getDiscordWebhookUrl(), message(event, messageKey));
        } catch (RuntimeException e) {
            // Only the type: the message of an HTTP client error contains the URL, and the webhook URL is a secret
            log.warn("Discord notification for event {} failed: {}", eventId, e.getClass().getSimpleName());
        }
    }

    /**
     * Groups have no language setting, so the message uses the language of the event's creator.
     * {@code <t:...:F>} is a Discord timestamp: each reader sees the date in their own time zone.
     */
    private String message(Event event, String messageKey) {
        Locale locale = event.getCreatedBy() == null ? Locale.ENGLISH
                : Locale.forLanguageTag(event.getCreatedBy().getLocale());
        String startsAt = "<t:" + event.getStartsAt().getEpochSecond() + ":F>";
        String url = notificationProperties.eventUrl(event.getGroup().getId(), event.getId());
        return messageSource.getMessage(messageKey, new Object[] {event.getTitle(), startsAt, url}, locale);
    }
}
