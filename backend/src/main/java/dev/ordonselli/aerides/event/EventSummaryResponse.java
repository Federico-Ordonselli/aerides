package dev.ordonselli.aerides.event;

import java.time.Instant;
import java.util.UUID;

import dev.ordonselli.aerides.game.GameResponse;

/** One row of the event list or calendar. */
public record EventSummaryResponse(
        Long id,
        String title,
        GameResponse game,
        Instant startsAt,
        int durationMinutes,
        EventStatus status,
        boolean requiresApproval,
        UUID seriesId,
        long totalCapacity,
        long confirmedCount) {

    public static EventSummaryResponse of(Event event, GameResponse game, long totalCapacity, long confirmedCount) {
        return new EventSummaryResponse(event.getId(), event.getTitle(), game, event.getStartsAt(),
                event.getDurationMinutes(), event.getStatus(), event.isRequiresApproval(), event.getSeriesId(),
                totalCapacity, confirmedCount);
    }
}
