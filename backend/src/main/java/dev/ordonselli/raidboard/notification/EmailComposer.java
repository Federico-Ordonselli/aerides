package dev.ordonselli.raidboard.notification;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import dev.ordonselli.raidboard.event.Event;
import dev.ordonselli.raidboard.user.User;

/**
 * Renders event emails with Thymeleaf, in the recipient's language.
 * <p>
 * Texts live in {@code messages.properties} (English, the fallback) and {@code messages_it.properties};
 * templates read them with {@code #{...}}, so the HTML is the same for every language.
 */
@Component
public class EmailComposer {

    private final ITemplateEngine templateEngine;
    private final MessageSource messageSource;
    private final NotificationProperties properties;

    public EmailComposer(ITemplateEngine templateEngine, MessageSource messageSource,
            NotificationProperties properties) {
        this.templateEngine = templateEngine;
        this.messageSource = messageSource;
        this.properties = properties;
    }

    /** {@code event} must have its group loaded. */
    public EmailMessage compose(NotificationType type, User recipient, Event event) {
        Locale locale = Locale.forLanguageTag(recipient.getLocale());
        String startsAt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(properties.timeZone())
                .format(event.getStartsAt());

        Context context = new Context(locale, Map.of(
                "displayName", recipient.getDisplayName(),
                "eventTitle", event.getTitle(),
                "groupName", event.getGroup().getName(),
                "startsAt", startsAt,
                "eventUrl", properties.eventUrl(event.getGroup().getId(), event.getId())));
        String html = templateEngine.process("email/" + type.templateName(), context);
        String subject = messageSource.getMessage("email." + type.templateName() + ".subject",
                new Object[] {event.getTitle()}, locale);
        return new EmailMessage(recipient.getEmail(), subject, html);
    }
}
