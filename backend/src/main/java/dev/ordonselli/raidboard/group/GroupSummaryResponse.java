package dev.ordonselli.raidboard.group;

/** A row of "my groups". Built directly by a JPQL constructor expression in MembershipRepository. */
public record GroupSummaryResponse(
        Long id,
        String name,
        String slug,
        String description,
        MembershipRole myRole,
        Long memberCount) {
}
