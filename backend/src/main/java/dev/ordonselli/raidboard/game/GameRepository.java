package dev.ordonselli.raidboard.game;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findAllByGroupIdOrderByNameAsc(Long groupId);

    /** Always look games up together with their group: an id from another group must not be found. */
    Optional<Game> findByIdAndGroupId(Long id, Long groupId);

    boolean existsByGroupIdAndRawgId(Long groupId, Long rawgId);
}
