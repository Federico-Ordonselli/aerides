package dev.ordonselli.raidboard.group;

import java.time.Instant;

/** A member as seen by the other members. The email is not included: members only see display names. */
public record MemberResponse(
        Long userId,
        String displayName,
        String avatarUrl,
        MembershipRole role,
        Instant joinedAt) {
}
