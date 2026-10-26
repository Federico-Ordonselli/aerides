package dev.ordonselli.raidboard.group;

/** Roles inside a group, from the weakest to the strongest: the declaration order matters. */
public enum MembershipRole {
    MEMBER,
    OFFICER,
    OWNER;

    /** True if this role has at least the permissions of {@code required} (OWNER > OFFICER > MEMBER). */
    public boolean isAtLeast(MembershipRole required) {
        return compareTo(required) >= 0;
    }
}
