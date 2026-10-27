package dev.ordonselli.aerides.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ordonselli.aerides.game.GameResponse;
import dev.ordonselli.aerides.signup.SignupResponse;

/**
 * An event with its slots and sign-ups.
 *
 * @param mySignup the caller's active sign-up, or null if they are not signed up
 * @param version  must be sent back with an update (optimistic locking)
 */
public record EventDetailResponse(
        Long id,
        String title,
        String description,
        GameResponse game,
        Instant startsAt,
        int durationMinutes,
        EventStatus status,
        boolean requiresApproval,
        int withdrawDeadlineHours,
        Instant withdrawDeadline,
        String recurrenceRule,
        UUID seriesId,
        List<SlotResponse> slots,
        SignupResponse mySignup,
        long version) {
}
