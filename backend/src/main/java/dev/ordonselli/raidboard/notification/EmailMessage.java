package dev.ordonselli.raidboard.notification;

/** A rendered email, ready to be sent. */
public record EmailMessage(String to, String subject, String html) {
}
