package dev.ordonselli.raidboard.event;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    /** Always look events up together with their group: an id from another group must not be found. */
    Optional<Event> findByIdAndGroupId(Long id, Long groupId);

    /** Same as the inherited method, but loads each event's game in the same query (no N+1 on the list page). */
    @Override
    @EntityGraph(attributePaths = "game")
    Page<Event> findAll(Specification<Event> specification, Pageable pageable);
}
