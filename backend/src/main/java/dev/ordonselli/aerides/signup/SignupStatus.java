package dev.ordonselli.aerides.signup;

import java.util.List;

public enum SignupStatus {
    /** Has a seat. */
    CONFIRMED,
    /** The slot was full: waits in FIFO order (by position) and is promoted when a seat frees up. */
    WAITLISTED,
    /** The event requires approval: waits for an OFFICER to approve or reject it. */
    PENDING,
    REJECTED,
    WITHDRAWN;

    /** Statuses that count as "signed up". Matches the partial unique index uk_signups_active_user_event. */
    public static final List<SignupStatus> ACTIVE = List.of(CONFIRMED, WAITLISTED, PENDING);
}
