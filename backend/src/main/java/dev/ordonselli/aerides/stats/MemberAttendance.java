package dev.ordonselli.aerides.stats;

/**
 * Attendance of one member over the requested period.
 *
 * @param completedEvents completed events of the group since the member joined
 * @param attended        of those, the ones where the member had a confirmed seat
 * @param withdrawals     sign-ups the member withdrew, on any event of the period
 * @param attendanceRate  attended / completedEvents, between 0 and 1; null if there was nothing to attend
 */
public record MemberAttendance(Long userId, String displayName, long completedEvents, long attended,
        long withdrawals, Double attendanceRate) {

    public static MemberAttendance of(Long userId, String displayName, long completedEvents, long attended,
            long withdrawals) {
        Double rate = completedEvents == 0 ? null : (double) attended / completedEvents;
        return new MemberAttendance(userId, displayName, completedEvents, attended, withdrawals, rate);
    }
}
