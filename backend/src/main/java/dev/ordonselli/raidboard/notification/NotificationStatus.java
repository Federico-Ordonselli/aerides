package dev.ordonselli.raidboard.notification;

public enum NotificationStatus {
    PENDING,
    SENT,
    /** The mail server refused it or was unreachable; retried while attempts are left (see FailedNotificationRetrier). */
    FAILED,
    /** Failed, and no longer worth sending: the event started, was cancelled, or the user lost their seat. */
    ABANDONED
}
