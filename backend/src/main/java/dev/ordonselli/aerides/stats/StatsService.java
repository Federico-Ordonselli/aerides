package dev.ordonselli.aerides.stats;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ordonselli.aerides.common.error.BadRequestException;

@Service
public class StatsService {

    /** Without an explicit period, the last 12 months (the current one included). */
    static final int DEFAULT_MONTHS = 12;

    /** Longer periods would make the monthly chart unreadable, and the queries scan more rows. */
    static final int MAX_MONTHS = 36;

    static final int UNFILLED_ROLES_LIMIT = 5;

    private final StatsRepository statsRepository;
    private final Clock clock;

    public StatsService(StatsRepository statsRepository, Clock clock) {
        this.statsRepository = statsRepository;
        this.clock = clock;
    }

    // readOnly: the three queries share one connection, and the database knows nothing will be written
    @Transactional(readOnly = true)
    public GroupStatsResponse forGroup(Long groupId, Instant from, Instant to, String zoneId) {
        ZoneId zone = parseZone(zoneId);
        Instant end = to != null ? to : clock.instant();
        Instant start = from != null ? from
                : YearMonth.from(end.atZone(zone)).minusMonths(DEFAULT_MONTHS - 1).atDay(1).atStartOfDay(zone)
                        .toInstant();
        if (!start.isBefore(end)) {
            throw new BadRequestException("'from' must be before 'to'");
        }
        if (start.atZone(zone).plusMonths(MAX_MONTHS).toInstant().isBefore(end)) {
            throw new BadRequestException("The period can be at most " + MAX_MONTHS + " months long");
        }

        List<MonthlyEventCount> months = statsRepository.eventsPerMonth(groupId, start, end, zone.getId());
        return new GroupStatsResponse(start, end, zone.getId(),
                statsRepository.attendance(groupId, start, end),
                fillMissingMonths(months, YearMonth.from(start.atZone(zone)),
                        YearMonth.from(end.minusNanos(1).atZone(zone))),
                statsRepository.unfilledRoles(groupId, start, end, UNFILLED_ROLES_LIMIT));
    }

    /** A chart needs every month on its axis, also the ones without events. */
    static List<MonthlyEventCount> fillMissingMonths(List<MonthlyEventCount> counts, YearMonth first,
            YearMonth last) {
        Map<YearMonth, MonthlyEventCount> byMonth = counts.stream()
                .collect(Collectors.toMap(MonthlyEventCount::month, Function.identity()));
        List<MonthlyEventCount> result = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            result.add(byMonth.getOrDefault(month, new MonthlyEventCount(month, 0, 0)));
        }
        return result;
    }

    private static ZoneId parseZone(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(zoneId);
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown time zone: " + zoneId);
        }
    }
}
