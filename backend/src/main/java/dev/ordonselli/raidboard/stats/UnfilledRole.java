package dev.ordonselli.raidboard.stats;

/**
 * A role that often stayed short of players on completed events.
 *
 * @param events    completed events that had a slot with this role
 * @param capacity  seats offered for this role, summed over those events
 * @param confirmed seats actually taken
 * @param missing   capacity - confirmed
 */
public record UnfilledRole(String roleName, long events, long capacity, long confirmed, long missing) {
}
