package dev.ordonselli.raidboard.stats;

import java.time.YearMonth;

/** How many events of one month were completed and how many were cancelled. */
public record MonthlyEventCount(YearMonth month, long completed, long cancelled) {
}
