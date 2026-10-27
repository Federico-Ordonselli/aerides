package dev.ordonselli.aerides.notification;

/** Each type has an email template in {@code templates/email/<templateName>.html}. */
public enum NotificationType {
    SIGNUP_PROMOTED("signup-promoted"),
    EVENT_REMINDER("event-reminder");

    private final String templateName;

    NotificationType(String templateName) {
        this.templateName = templateName;
    }

    public String templateName() {
        return templateName;
    }
}
