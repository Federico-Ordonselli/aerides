package dev.ordonselli.raidboard.group;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    Optional<Membership> findByGroupIdAndUserId(Long groupId, Long userId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    long countByGroupId(Long groupId);

    /** Members with their user loaded in the same query ("join fetch"), to avoid one query per member. */
    @Query("select m from Membership m join fetch m.user where m.group.id = :groupId")
    List<Membership> findAllWithUserByGroupId(@Param("groupId") Long groupId);

    /** The groups of a user with their role and member count, in a single query. */
    @Query("""
            select new dev.ordonselli.raidboard.group.GroupSummaryResponse(
                g.id, g.name, g.slug, g.description, m.role,
                (select count(other) from Membership other where other.group = g))
            from Membership m join m.group g
            where m.user.id = :userId
            order by lower(g.name)
            """)
    List<GroupSummaryResponse> findSummariesByUserId(@Param("userId") Long userId);
}
