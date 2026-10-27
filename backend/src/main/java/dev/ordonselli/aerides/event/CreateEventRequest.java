package dev.ordonselli.aerides.event;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param weeklyOccurrences null for a single event; otherwise how many weekly events to create (2 to 26)
 * @param timeZone the IANA zone of the organizer, e.g. "Europe/Rome". Required for a weekly series, so
 *                 that "every Thursday at 21:00" stays at 21:00 local time across daylight saving changes
 */
public record CreateEventRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String description,
        Long gameId,
        @NotNull @Future Instant startsAt,
        @NotNull @Min(15) @Max(1440) Integer durationMinutes,
        @NotNull Boolean requiresApproval,
        @NotNull @Min(0) @Max(168) Integer withdrawDeadlineHours,
        @NotEmpty @Size(max = 10) List<@Valid @NotNull SlotRequest> slots,
        @Min(2) @Max(26) Integer weeklyOccurrences,
        @Size(max = 64) String timeZone) {
}
