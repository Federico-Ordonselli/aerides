package dev.ordonselli.raidboard.event;

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
 * Replaces the editable fields of one event (one occurrence, for a series).
 *
 * @param slots   the complete list: slots with an id are updated, slots without one are added,
 *                existing slots missing from the list are removed
 * @param version the version the client loaded; if the event changed since, the update is rejected
 */
public record UpdateEventRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String description,
        Long gameId,
        @NotNull @Future Instant startsAt,
        @NotNull @Min(15) @Max(1440) Integer durationMinutes,
        @NotNull Boolean requiresApproval,
        @NotNull @Min(0) @Max(168) Integer withdrawDeadlineHours,
        @NotEmpty @Size(max = 10) List<@Valid @NotNull SlotRequest> slots,
        @NotNull Long version) {
}
