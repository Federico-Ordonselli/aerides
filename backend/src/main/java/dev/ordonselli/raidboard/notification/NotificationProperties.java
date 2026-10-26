package dev.ordonselli.raidboard.notification;

import java.time.Duration;
import java.time.ZoneId;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param appUrl          public address of the frontend, for links in emails and Discord messages
 * @param mailFrom        sender of every email
 * @param timeZone        zone used to write dates in emails (users don't have their own zone yet)
 * @param reminderLeadTime how long before the start of an event the reminder goes out
 */
@Validated
@ConfigurationProperties(prefix = "raidboard.notifications")
public record NotificationProperties(
        @NotBlank String appUrl,
        @NotBlank String mailFrom,
        @NotNull ZoneId timeZone,
        @NotNull Duration reminderLeadTime) {

    public String eventUrl(Long groupId, Long eventId) {
        return appUrl + "/groups/" + groupId + "/events/" + eventId;
    }
}
