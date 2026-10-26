package dev.ordonselli.raidboard.event;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ordonselli.raidboard.audit.AuditAction;
import dev.ordonselli.raidboard.audit.AuditEntityType;
import dev.ordonselli.raidboard.audit.AuditService;
import dev.ordonselli.raidboard.common.error.BadRequestException;
import dev.ordonselli.raidboard.common.error.BusinessRuleException;
import dev.ordonselli.raidboard.common.error.ConflictException;
import dev.ordonselli.raidboard.common.error.NotFoundException;
import dev.ordonselli.raidboard.common.web.PageResponse;
import dev.ordonselli.raidboard.game.Game;
import dev.ordonselli.raidboard.game.GameMapper;
import dev.ordonselli.raidboard.game.GameRepository;
import dev.ordonselli.raidboard.group.GroupRepository;
import dev.ordonselli.raidboard.group.Membership;
import dev.ordonselli.raidboard.group.MembershipRepository;
import dev.ordonselli.raidboard.group.MembershipRole;
import dev.ordonselli.raidboard.signup.Signup;
import dev.ordonselli.raidboard.signup.SignupMapper;
import dev.ordonselli.raidboard.signup.SignupRepository;
import dev.ordonselli.raidboard.signup.SignupResponse;
import dev.ordonselli.raidboard.signup.SignupStatus;
import dev.ordonselli.raidboard.signup.WaitlistService;
import dev.ordonselli.raidboard.user.UserRepository;

/**
 * Event lifecycle. Role checks happen in the controller ({@code @PreAuthorize}); every lookup here
 * still filters by group id, so an event of another group is never found through this group's URL.
 */
@Service
public class EventService {

    private static final String WEEKLY_RULE = "FREQ=WEEKLY;COUNT=%d";

    private final EventRepository eventRepository;
    private final EventSlotRepository slotRepository;
    private final SignupRepository signupRepository;
    private final GameRepository gameRepository;
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final WaitlistService waitlistService;
    private final GameMapper gameMapper;
    private final SignupMapper signupMapper;
    private final AuditService auditService;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public EventService(EventRepository eventRepository, EventSlotRepository slotRepository,
            SignupRepository signupRepository, GameRepository gameRepository, GroupRepository groupRepository,
            MembershipRepository membershipRepository, UserRepository userRepository, WaitlistService waitlistService,
            GameMapper gameMapper, SignupMapper signupMapper, AuditService auditService, EntityManager entityManager,
            ApplicationEventPublisher eventPublisher, Clock clock) {
        this.eventRepository = eventRepository;
        this.slotRepository = slotRepository;
        this.signupRepository = signupRepository;
        this.gameRepository = gameRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.waitlistService = waitlistService;
        this.gameMapper = gameMapper;
        this.signupMapper = signupMapper;
        this.auditService = auditService;
        this.entityManager = entityManager;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Creates one event, or a weekly series of real events sharing a series id.
     * Returns the first occurrence.
     */
    @Transactional
    public EventDetailResponse create(Long groupId, Long actorId, CreateEventRequest request) {
        requireUniqueRoleNames(request.slots());
        Game game = findGame(groupId, request.gameId());
        int occurrences = request.weeklyOccurrences() == null ? 1 : request.weeklyOccurrences();
        UUID seriesId = occurrences > 1 ? UUID.randomUUID() : null;
        // Weeks are added in the organizer's zone: 21:00 in Rome stays 21:00 after the switch to summer time
        ZonedDateTime firstStart = request.startsAt().atZone(occurrences > 1 ? seriesZone(request) : ZoneOffset.UTC);

        Event first = null;
        for (int week = 0; week < occurrences; week++) {
            Event event = new Event(groupRepository.getReferenceById(groupId), userRepository.getReferenceById(actorId),
                    request.title().trim(), firstStart.plusWeeks(week).toInstant(), request.durationMinutes());
            event.setDescription(trimToNull(request.description()));
            event.setGame(game);
            event.setRequiresApproval(request.requiresApproval());
            event.setWithdrawDeadlineHours(request.withdrawDeadlineHours());
            if (seriesId != null) {
                event.setSeries(seriesId, WEEKLY_RULE.formatted(occurrences));
            }
            for (SlotRequest slot : request.slots()) {
                event.addSlot(slot.roleName().trim(), slot.capacity());
            }
            eventRepository.save(event);
            auditService.record(groupId, actorId, AuditAction.EVENT_CREATED, AuditEntityType.EVENT, event.getId(),
                    auditDetails(event));
            if (first == null) {
                first = event;
            }
        }
        return toDetail(first, actorId);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> list(Long groupId, Long userId, EventFilter filter, Pageable pageable) {
        boolean includeDrafts = roleOf(groupId, userId).isAtLeast(MembershipRole.OFFICER);
        Page<Event> page = eventRepository.findAll(EventSpecifications.matching(groupId, filter, includeDrafts),
                pageable);

        // Two GROUP BY queries for the whole page instead of two queries per event
        List<Long> ids = page.getContent().stream().map(Event::getId).toList();
        Map<Long, Long> capacities = ids.isEmpty() ? Map.of() : toMap(slotRepository.sumCapacityByEventIds(ids));
        Map<Long, Long> confirmed = ids.isEmpty() ? Map.of()
                : toMap(signupRepository.countByEventIdsAndStatus(ids, SignupStatus.CONFIRMED));
        return PageResponse.of(page.map(event -> EventSummaryResponse.of(event,
                gameMapper.toResponse(event.getGame()),
                capacities.getOrDefault(event.getId(), 0L),
                confirmed.getOrDefault(event.getId(), 0L))));
    }

    @Transactional(readOnly = true)
    public EventDetailResponse get(Long groupId, Long eventId, Long userId) {
        Event event = requireEvent(groupId, eventId);
        // Drafts are work in progress: for members they don't exist
        if (event.getStatus() == EventStatus.DRAFT && !roleOf(groupId, userId).isAtLeast(MembershipRole.OFFICER)) {
            throw new NotFoundException("Event not found");
        }
        return toDetail(event, userId);
    }

    /** Edits a single event (one occurrence of a series): the other occurrences are not touched. */
    @Transactional
    public EventDetailResponse update(Long groupId, Long eventId, Long actorId, UpdateEventRequest request) {
        Event event = requireEvent(groupId, eventId);
        // The client sends the version it loaded: if someone saved in the meantime, don't overwrite their changes
        if (event.getVersion() != request.version()) {
            throw new ConflictException("The event was modified by someone else, reload it and try again");
        }
        if (!event.isEditable()) {
            throw new ConflictException("A cancelled or completed event can't be edited");
        }
        requireUniqueRoleNames(request.slots());
        // @Version only changes when a column of the events row changes, not when a slot does.
        // Force the increment, so that slot edits are covered by optimistic locking too.
        entityManager.lock(event, LockModeType.PESSIMISTIC_FORCE_INCREMENT);

        List<String> changed = new ArrayList<>();
        apply(changed, "title", event.getTitle(), request.title().trim(), event::setTitle);
        apply(changed, "description", event.getDescription(), trimToNull(request.description()),
                event::setDescription);
        apply(changed, "game", event.getGame() == null ? null : event.getGame().getId(), request.gameId(),
                gameId -> event.setGame(findGame(groupId, gameId)));
        apply(changed, "startsAt", event.getStartsAt(), request.startsAt(), event::setStartsAt);
        apply(changed, "durationMinutes", event.getDurationMinutes(), request.durationMinutes(),
                event::setDurationMinutes);
        apply(changed, "requiresApproval", event.isRequiresApproval(), request.requiresApproval(),
                event::setRequiresApproval);
        apply(changed, "withdrawDeadlineHours", event.getWithdrawDeadlineHours(), request.withdrawDeadlineHours(),
                event::setWithdrawDeadlineHours);
        if (updateSlots(event, request.slots())) {
            changed.add("slots");
        }

        if (!changed.isEmpty()) {
            auditService.record(groupId, actorId, AuditAction.EVENT_UPDATED, AuditEntityType.EVENT, eventId,
                    Map.of("changed", changed));
            eventPublisher.publishEvent(new EventChangedEvent(groupId, eventId, EventChangeType.DETAILS));
        }
        return flushAndGetDetail(event, actorId);
    }

    @Transactional
    public EventDetailResponse publish(Long groupId, Long eventId, Long actorId) {
        Event event = requireEvent(groupId, eventId);
        if (!event.getStartsAt().isAfter(clock.instant())) {
            throw new BusinessRuleException("An event in the past can't be published");
        }
        event.publish();
        auditService.record(groupId, actorId, AuditAction.EVENT_PUBLISHED, AuditEntityType.EVENT, eventId);
        eventPublisher.publishEvent(new EventChangedEvent(groupId, eventId, EventChangeType.STATUS));
        eventPublisher.publishEvent(new EventPublishedEvent(groupId, eventId));
        return flushAndGetDetail(event, actorId);
    }

    /** Sign-ups keep their status, so the history of who was going is not lost. */
    @Transactional
    public EventDetailResponse cancel(Long groupId, Long eventId, Long actorId) {
        Event event = requireEvent(groupId, eventId);
        event.cancel();
        auditService.record(groupId, actorId, AuditAction.EVENT_CANCELLED, AuditEntityType.EVENT, eventId);
        eventPublisher.publishEvent(new EventChangedEvent(groupId, eventId, EventChangeType.STATUS));
        return flushAndGetDetail(event, actorId);
    }

    @Transactional
    public EventDetailResponse complete(Long groupId, Long eventId, Long actorId) {
        Event event = requireEvent(groupId, eventId);
        if (event.getStartsAt().isAfter(clock.instant())) {
            throw new BusinessRuleException("An event can be completed only after it has started");
        }
        event.complete();
        auditService.record(groupId, actorId, AuditAction.EVENT_COMPLETED, AuditEntityType.EVENT, eventId);
        eventPublisher.publishEvent(new EventChangedEvent(groupId, eventId, EventChangeType.STATUS));
        return flushAndGetDetail(event, actorId);
    }

    /**
     * Applies the requested slot list to the event. Returns true if anything changed.
     * <ul>
     *   <li>a slot with active sign-ups can't be removed;</li>
     *   <li>capacity can't go below the number of confirmed participants;</li>
     *   <li>more capacity promotes waitlisted users straight away.</li>
     * </ul>
     */
    private boolean updateSlots(Event event, List<SlotRequest> requested) {
        // Same lock sign-ups take: nobody can take a seat while the seats are being counted here
        Map<Long, EventSlot> existing = slotRepository.findAllByEventIdForUpdate(event.getId()).stream()
                .collect(Collectors.toMap(EventSlot::getId, Function.identity()));
        Set<Long> keptIds = new HashSet<>();
        for (SlotRequest slot : requested) {
            if (slot.id() != null) {
                if (!existing.containsKey(slot.id())) {
                    throw new BadRequestException("Slot " + slot.id() + " does not belong to this event");
                }
                keptIds.add(slot.id());
            }
        }

        boolean changed = false;
        for (EventSlot slot : existing.values()) {
            if (!keptIds.contains(slot.getId())) {
                if (signupRepository.countBySlotIdAndStatusIn(slot.getId(), SignupStatus.ACTIVE) > 0) {
                    throw new ConflictException("The slot '" + slot.getRoleName()
                            + "' has sign-ups and can't be removed");
                }
                event.removeSlot(slot);
                changed = true;
            }
        }
        // Run the DELETEs before the INSERTs below: a new slot may reuse the role name of a removed one
        // (unique constraint), and Hibernate would otherwise insert first
        eventRepository.flush();

        for (SlotRequest request : requested) {
            String roleName = request.roleName().trim();
            if (request.id() == null) {
                event.addSlot(roleName, request.capacity());
                changed = true;
                continue;
            }
            EventSlot slot = existing.get(request.id());
            if (!roleName.equals(slot.getRoleName())) {
                slot.setRoleName(roleName);
                changed = true;
            }
            if (request.capacity() != slot.getCapacity()) {
                changeCapacity(slot, request.capacity());
                changed = true;
            }
        }
        return changed;
    }

    private void changeCapacity(EventSlot slot, int newCapacity) {
        long confirmed = signupRepository.countBySlotIdAndStatus(slot.getId(), SignupStatus.CONFIRMED);
        if (newCapacity < confirmed) {
            throw new ConflictException("The slot '" + slot.getRoleName() + "' has " + confirmed
                    + " confirmed participants: its capacity can't be lower");
        }
        boolean grew = newCapacity > slot.getCapacity();
        slot.setCapacity(newCapacity);
        if (grew) {
            waitlistService.promote(slot);
        }
    }

    private EventDetailResponse flushAndGetDetail(Event event, Long userId) {
        // Write the changes now: the version is incremented on flush, and the response must carry the new one
        eventRepository.flush();
        return toDetail(event, userId);
    }

    private EventDetailResponse toDetail(Event event, Long userId) {
        List<Signup> signups = signupRepository.findAllWithUserByEventIdAndStatusIn(event.getId(),
                SignupStatus.ACTIVE);
        Map<Long, List<Signup>> bySlot = signups.stream()
                .collect(Collectors.groupingBy(signup -> signup.getSlot().getId()));
        List<SlotResponse> slots = event.getSlots().stream()
                .map(slot -> {
                    List<Signup> ofSlot = bySlot.getOrDefault(slot.getId(), List.of());
                    return new SlotResponse(slot.getId(), slot.getRoleName(), slot.getCapacity(),
                            withStatus(ofSlot, SignupStatus.CONFIRMED),
                            withStatus(ofSlot, SignupStatus.WAITLISTED),
                            withStatus(ofSlot, SignupStatus.PENDING));
                })
                .toList();
        SignupResponse mySignup = signups.stream()
                .filter(signup -> signup.getUser().getId().equals(userId))
                .findFirst()
                .map(signupMapper::toResponse)
                .orElse(null);
        return new EventDetailResponse(event.getId(), event.getTitle(), event.getDescription(),
                gameMapper.toResponse(event.getGame()), event.getStartsAt(), event.getDurationMinutes(),
                event.getStatus(), event.isRequiresApproval(), event.getWithdrawDeadlineHours(),
                event.getWithdrawDeadline(), event.getRecurrenceRule(), event.getSeriesId(), slots, mySignup,
                event.getVersion());
    }

    private List<SignupResponse> withStatus(List<Signup> signups, SignupStatus status) {
        return signups.stream()
                .filter(signup -> signup.getStatus() == status)
                .map(signupMapper::toResponse)
                .toList();
    }

    private Event requireEvent(Long groupId, Long eventId) {
        return eventRepository.findByIdAndGroupId(eventId, groupId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
    }

    private MembershipRole roleOf(Long groupId, Long userId) {
        return membershipRepository.findByGroupIdAndUserId(groupId, userId)
                .map(Membership::getRole)
                .orElseThrow(() -> new NotFoundException("Group not found"));
    }

    /** The game must be in this group's catalog: a game id from another group is "not found". */
    private Game findGame(Long groupId, Long gameId) {
        if (gameId == null) {
            return null;
        }
        return gameRepository.findByIdAndGroupId(gameId, groupId)
                .orElseThrow(() -> new NotFoundException("Game not found"));
    }

    private static ZoneId seriesZone(CreateEventRequest request) {
        if (request.timeZone() == null || request.timeZone().isBlank()) {
            throw new BadRequestException("timeZone is required for a weekly series");
        }
        try {
            return ZoneId.of(request.timeZone());
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown time zone: " + request.timeZone());
        }
    }

    private static void requireUniqueRoleNames(List<SlotRequest> slots) {
        Set<String> names = new HashSet<>();
        for (SlotRequest slot : slots) {
            if (!names.add(slot.roleName().trim().toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("Role names must be unique within an event: '"
                        + slot.roleName().trim() + "' is repeated");
            }
        }
    }

    /** Sets the new value and records the field name, only if the value really changed. */
    private static <T> void apply(List<String> changed, String field, T current, T requested, Consumer<T> setter) {
        if (!Objects.equals(current, requested)) {
            setter.accept(requested);
            changed.add(field);
        }
    }

    private static Map<String, Object> auditDetails(Event event) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("title", event.getTitle());
        details.put("startsAt", event.getStartsAt().toString());
        if (event.getSeriesId() != null) {
            details.put("seriesId", event.getSeriesId().toString());
        }
        return details;
    }

    private static Map<Long, Long> toMap(List<EventCount> counts) {
        return counts.stream().collect(Collectors.toMap(EventCount::eventId, EventCount::count));
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
