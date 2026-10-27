package dev.ordonselli.aerides.group;

/**
 * Published when a user loses access to some of a group's data: they were removed, they left, or
 * they were demoted below officer (so they can no longer read drafts). After the commit, the realtime
 * layer closes their open WebSocket sessions, so subscriptions authorized with the old role don't
 * keep receiving updates.
 */
public record GroupAccessRevokedEvent(Long groupId, Long userId) {
}
