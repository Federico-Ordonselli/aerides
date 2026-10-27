package dev.ordonselli.aerides.group;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByInviteCode(String inviteCode);

    boolean existsBySlug(String slug);

    boolean existsByInviteCode(String inviteCode);
}
