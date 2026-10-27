package dev.ordonselli.aerides.group;

/**
 * Published inside the transaction that deletes a membership: the user left the group or was removed.
 * Listeners that run synchronously ({@code @EventListener}) clean up what the user had in the group in
 * the same transaction, so the membership and, for example, its sign-ups go away together.
 * <p>
 * Unlike {@link GroupAccessRevokedEvent}, a demotion doesn't end the membership.
 */
public record MembershipEndedEvent(Long groupId, Long userId) {
}
