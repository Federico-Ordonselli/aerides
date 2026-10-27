package dev.ordonselli.aerides.stats;

import java.time.Instant;
import java.util.List;

/** Statistics of a group for the period [from, to), with months computed in {@code zone}. */
public record GroupStatsResponse(Instant from, Instant to, String zone, List<MemberAttendance> attendance,
        List<MonthlyEventCount> eventsPerMonth, List<UnfilledRole> unfilledRoles) {
}
