package dev.ordonselli.raidboard.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.ordonselli.raidboard.common.error.BadRequestException;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    private static final Long GROUP_ID = 1L;
    private static final Instant NOW = Instant.parse("2027-03-15T10:00:00Z");

    @Mock
    private StatsRepository statsRepository;

    private StatsService statsService;

    @BeforeEach
    void setUp() {
        statsService = new StatsService(statsRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void withoutAPeriodItCoversTheLastTwelveMonthsInTheGivenZone() {
        GroupStatsResponse stats = statsService.forGroup(GROUP_ID, null, null, "Europe/Rome");

        // 1 April 2026, midnight in Rome (UTC+2 in summer time)
        Instant expectedFrom = Instant.parse("2026-03-31T22:00:00Z");
        assertThat(stats.from()).isEqualTo(expectedFrom);
        assertThat(stats.to()).isEqualTo(NOW);
        assertThat(stats.zone()).isEqualTo("Europe/Rome");
        verify(statsRepository).attendance(GROUP_ID, expectedFrom, NOW);
        verify(statsRepository).eventsPerMonth(GROUP_ID, expectedFrom, NOW, "Europe/Rome");
        verify(statsRepository).unfilledRoles(GROUP_ID, expectedFrom, NOW, StatsService.UNFILLED_ROLES_LIMIT);
    }

    @Test
    void everyMonthOfThePeriodIsReturnedAlsoWithoutEvents() {
        when(statsRepository.eventsPerMonth(eq(GROUP_ID), any(), any(), eq("UTC")))
                .thenReturn(List.of(new MonthlyEventCount(YearMonth.of(2027, 2), 3, 1)));

        GroupStatsResponse stats = statsService.forGroup(GROUP_ID, Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2027-04-01T00:00:00Z"), null);

        // 'to' is exclusive: 1 April at midnight does not add April to the chart
        assertThat(stats.eventsPerMonth()).containsExactly(
                new MonthlyEventCount(YearMonth.of(2027, 1), 0, 0),
                new MonthlyEventCount(YearMonth.of(2027, 2), 3, 1),
                new MonthlyEventCount(YearMonth.of(2027, 3), 0, 0));
    }

    @Test
    void fromMustBeBeforeTo() {
        assertThatThrownBy(() -> statsService.forGroup(GROUP_ID, NOW, NOW, null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(statsRepository);
    }

    @Test
    void thePeriodCannotBeLongerThanThirtySixMonths() {
        Instant to = Instant.parse("2027-01-01T00:00:00Z");

        assertThatThrownBy(() -> statsService.forGroup(GROUP_ID, Instant.parse("2023-12-31T23:59:59Z"), to, null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(statsRepository);

        // Exactly 36 months is allowed
        statsService.forGroup(GROUP_ID, Instant.parse("2024-01-01T00:00:00Z"), to, null);
        verify(statsRepository).unfilledRoles(eq(GROUP_ID), any(), any(), anyInt());
    }

    @Test
    void anUnknownZoneIsABadRequest() {
        assertThatThrownBy(() -> statsService.forGroup(GROUP_ID, null, null, "Mars/Olympus_Mons"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Mars/Olympus_Mons");
    }

    @Test
    void theAttendanceRateIsMissingWhenThereWasNothingToAttend() {
        assertThat(MemberAttendance.of(1L, "Luca", 0, 0, 2).attendanceRate()).isNull();
        assertThat(MemberAttendance.of(1L, "Luca", 4, 3, 0).attendanceRate()).isEqualTo(0.75);
    }
}
