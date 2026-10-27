package dev.ordonselli.aerides.group;

import java.time.Instant;

/**
 * A group as seen by one of its members. What is visible depends on the member's role:
 * the invite code only for OFFICER and OWNER, the Discord webhook URL only for the OWNER.
 * <p>
 * Built by hand instead of MapStruct because these visibility rules are business logic,
 * and they are easier to read and test here than as mapping expressions.
 */
public record GroupDetailResponse(
        Long id,
        String name,
        String slug,
        String description,
        MembershipRole myRole,
        long memberCount,
        String inviteCode,
        boolean hasDiscordWebhook,
        String discordWebhookUrl,
        Instant createdAt) {

    public static GroupDetailResponse of(Group group, MembershipRole myRole, long memberCount) {
        return new GroupDetailResponse(
                group.getId(),
                group.getName(),
                group.getSlug(),
                group.getDescription(),
                myRole,
                memberCount,
                myRole.isAtLeast(MembershipRole.OFFICER) ? group.getInviteCode() : null,
                group.getDiscordWebhookUrl() != null,
                myRole == MembershipRole.OWNER ? group.getDiscordWebhookUrl() : null,
                group.getCreatedAt());
    }
}
