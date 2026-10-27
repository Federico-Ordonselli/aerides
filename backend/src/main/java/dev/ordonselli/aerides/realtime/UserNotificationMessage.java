package dev.ordonselli.aerides.realtime;

/** Sent on {@code /user/queue/notifications}: a personal message shown as a toast. */
public record UserNotificationMessage(Type type, Long groupId, Long eventId, String eventTitle) {

    public enum Type {
        SIGNUP_PROMOTED
    }
}
