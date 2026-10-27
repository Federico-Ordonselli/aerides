package dev.ordonselli.aerides.notification;

/** A rendered email, ready to be sent. */
public record EmailMessage(String to, String subject, String html) {
}
