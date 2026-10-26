package dev.ordonselli.raidboard.event;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the WHERE clause of the event list from the optional filters (JPA Criteria API).
 * Only the filters that are set become conditions, so one query method covers every combination.
 */
final class EventSpecifications {

    private EventSpecifications() {
    }

    static Specification<Event> matching(Long groupId, EventFilter filter, boolean includeDrafts) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("group").get("id"), groupId));
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("startsAt"), filter.to()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.gameId() != null) {
                predicates.add(cb.equal(root.get("game").get("id"), filter.gameId()));
            }
            if (!includeDrafts) {
                predicates.add(cb.notEqual(root.get("status"), EventStatus.DRAFT));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
