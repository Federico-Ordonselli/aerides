package dev.ordonselli.raidboard.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import dev.ordonselli.raidboard.audit.AuditAction;
import dev.ordonselli.raidboard.audit.AuditEntityType;
import dev.ordonselli.raidboard.audit.AuditService;
import dev.ordonselli.raidboard.common.error.BadRequestException;
import dev.ordonselli.raidboard.common.error.BusinessRuleException;
import dev.ordonselli.raidboard.common.error.ConflictException;
import dev.ordonselli.raidboard.common.error.NotFoundException;
import dev.ordonselli.raidboard.game.GameMapperImpl;
import dev.ordonselli.raidboard.game.GameRepository;
import dev.ordonselli.raidboard.group.Group;
import dev.ordonselli.raidboard.group.GroupRepository;
import dev.ordonselli.raidboard.group.Membership;
import dev.ordonselli.raidboard.group.MembershipRepository;
import dev.ordonselli.raidboard.group.MembershipRole;
import dev.ordonselli.raidboard.signup.SignupMapperImpl;
import dev.ordonselli.raidboard.signup.SignupRepository;
import dev.ordonselli.raidboard.signup.SignupStatus;
import dev.ordonselli.raidboard.signup.WaitlistService;
import dev.ordonselli.raidboard.user.User;
import dev.ordonselli.raidboard.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    private static final Long GROUP_ID = 1L;
    private static final Long EVENT_ID = 10L;
    private static final Long SLOT_ID = 100L;
    private static final Long OFFICER_ID = 2L;
    private static final Instant NOW = Instant.parse("2027-01-10T12:00:00Z");
    private static final Instant NEXT_WEEK = NOW.plus(Duration.ofDays(7));

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventSlotRepository slotRepository;
    @Mock
    private SignupRepository signupRepository;
    @Mock
    private GameRepository gameRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WaitlistService waitlistService;
    @Mock
    private AuditService auditService;
    @Mock
    private EntityManager entityManager;

    private EventService eventService;
    private Group group;
    private Event event;
    private EventSlot slot;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, slotRepository, signupRepository, gameRepository,
                groupRepository, membershipRepository, userRepository, waitlistService, new GameMapperImpl(),
                new SignupMapperImpl(), auditService, entityManager, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
        group = new Group("Gilda", "gilda", null, "ABCDEFGHJK");
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        event = new Event(group, null, "Raid", NEXT_WEEK, 120);
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        slot = event.addSlot("Tank", 2);
        ReflectionTestUtils.setField(slot, "id", SLOT_ID);
    }

    @Test
    void createMakesADraftWithItsSlotsAndWritesAnAuditEntry() {
        givenSavedEventsGetIds();

        EventDetailResponse response = eventService.create(GROUP_ID, OFFICER_ID,
                createRequest(NEXT_WEEK, null, null, slot("  Tank ", 2), slot("Healer", 1)));

        assertThat(response.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(response.seriesId()).isNull();
        assertThat(response.slots()).extracting(SlotResponse::roleName).containsExactly("Tank", "Healer");
        verify(auditService).record(eq(GROUP_ID), eq(OFFICER_ID), eq(AuditAction.EVENT_CREATED),
                eq(AuditEntityType.EVENT), any(), anyMap());
    }

    @Test
    void aWeeklySeriesIsMadeOfRealEventsAtTheSameLocalTime() {
        givenSavedEventsGetIds();
        // 21:00 in Rome; summer time starts on 28 March 2027
        Instant firstStart = Instant.parse("2027-03-18T20:00:00Z");

        eventService.create(GROUP_ID, OFFICER_ID, createRequest(firstStart, 3, "Europe/Rome", slot("DPS", 5)));

        ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository, times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Event::getStartsAt).containsExactly(
                Instant.parse("2027-03-18T20:00:00Z"),
                Instant.parse("2027-03-25T20:00:00Z"),
                Instant.parse("2027-04-01T19:00:00Z"));
        assertThat(saved.getAllValues()).extracting(Event::getSeriesId).doesNotContainNull()
                .containsOnly(saved.getAllValues().getFirst().getSeriesId());
        assertThat(saved.getAllValues()).extracting(Event::getRecurrenceRule).containsOnly("FREQ=WEEKLY;COUNT=3");
        verify(auditService, times(3)).record(eq(GROUP_ID), eq(OFFICER_ID), eq(AuditAction.EVENT_CREATED),
                eq(AuditEntityType.EVENT), any(), anyMap());
    }

    @Test
    void aWeeklySeriesNeedsAValidTimeZone() {
        assertThatThrownBy(() -> eventService.create(GROUP_ID, OFFICER_ID,
                createRequest(NEXT_WEEK, 3, null, slot("DPS", 5))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> eventService.create(GROUP_ID, OFFICER_ID,
                createRequest(NEXT_WEEK, 3, "Mars/Olympus", slot("DPS", 5))))
                .isInstanceOf(BadRequestException.class);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void roleNamesMustBeUniqueIgnoringCaseAndSpaces() {
        assertThatThrownBy(() -> eventService.create(GROUP_ID, OFFICER_ID,
                createRequest(NEXT_WEEK, null, null, slot("Tank", 1), slot(" tank", 1))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void theGameMustBelongToTheGroup() {
        when(gameRepository.findByIdAndGroupId(77L, GROUP_ID)).thenReturn(Optional.empty());
        CreateEventRequest request = new CreateEventRequest("Raid", null, 77L, NEXT_WEEK, 120, false, 0,
                List.of(slot("Tank", 1)), null, null);

        assertThatThrownBy(() -> eventService.create(GROUP_ID, OFFICER_ID, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void membersDoNotSeeDrafts() {
        givenEvent();
        givenRole(5L, MembershipRole.MEMBER);

        assertThatThrownBy(() -> eventService.get(GROUP_ID, EVENT_ID, 5L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anUpdateBasedOnAnOldVersionIsRejectedBeforeChangingAnything() {
        ReflectionTestUtils.setField(event, "version", 3L);
        givenEvent();

        assertThatThrownBy(() -> eventService.update(GROUP_ID, EVENT_ID, OFFICER_ID,
                updateRequest(2L, slot(SLOT_ID, "Tank", 2))))
                .isInstanceOf(ConflictException.class);
        assertThat(event.getTitle()).isEqualTo("Raid");
        verify(entityManager, never()).lock(any(), any());
    }

    @Test
    void everyUpdateIncrementsTheVersionSoSlotChangesAreCoveredToo() {
        givenEvent();
        givenLockedSlots();

        eventService.update(GROUP_ID, EVENT_ID, OFFICER_ID, updateRequest(0L, slot(SLOT_ID, "Tank", 2)));

        verify(entityManager).lock(event, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }

    @Test
    void moreCapacityPromotesTheWaitlist() {
        givenEvent();
        givenLockedSlots();

        eventService.update(GROUP_ID, EVENT_ID, OFFICER_ID, updateRequest(0L, slot(SLOT_ID, "Tank", 4)));

        assertThat(slot.getCapacity()).isEqualTo(4);
        verify(waitlistService).promote(slot);
        verify(auditService).record(GROUP_ID, OFFICER_ID, AuditAction.EVENT_UPDATED, AuditEntityType.EVENT,
                EVENT_ID, Map.of("changed", List.of("title", "slots")));
    }

    @Test
    void capacityCannotGoBelowTheConfirmedParticipants() {
        givenEvent();
        givenLockedSlots();
        when(signupRepository.countBySlotIdAndStatus(SLOT_ID, SignupStatus.CONFIRMED)).thenReturn(2L);

        assertThatThrownBy(() -> eventService.update(GROUP_ID, EVENT_ID, OFFICER_ID,
                updateRequest(0L, slot(SLOT_ID, "Tank", 1))))
                .isInstanceOf(ConflictException.class);
        verify(waitlistService, never()).promote(any());
    }

    @Test
    void aSlotWithSignupsCannotBeRemoved() {
        givenEvent();
        givenLockedSlots();
        when(signupRepository.countBySlotIdAndStatusIn(SLOT_ID, SignupStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> eventService.update(GROUP_ID, EVENT_ID, OFFICER_ID,
                updateRequest(0L, slot("Healer", 2))))
                .isInstanceOf(ConflictException.class);
        assertThat(event.getSlots()).containsExactly(slot);
    }

    @Test
    void aSlotOfAnotherEventCannotBeEdited() {
        givenEvent();
        givenLockedSlots();

        assertThatThrownBy(() -> eventService.update(GROUP_ID, EVENT_ID, OFFICER_ID,
                updateRequest(0L, slot(999L, "Tank", 2))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void aCancelledEventCannotBeEdited() {
        event.cancel();
        givenEvent();

        assertThatThrownBy(() -> eventService.update(GROUP_ID, EVENT_ID, OFFICER_ID,
                updateRequest(0L, slot(SLOT_ID, "Tank", 2))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void publishingMovesADraftToPublishedAndIsAudited() {
        givenEvent();

        EventDetailResponse response = eventService.publish(GROUP_ID, EVENT_ID, OFFICER_ID);

        assertThat(response.status()).isEqualTo(EventStatus.PUBLISHED);
        verify(auditService).record(GROUP_ID, OFFICER_ID, AuditAction.EVENT_PUBLISHED, AuditEntityType.EVENT,
                EVENT_ID);
        assertThatThrownBy(() -> eventService.publish(GROUP_ID, EVENT_ID, OFFICER_ID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void anEventInThePastCannotBePublished() {
        event.setStartsAt(NOW.minus(Duration.ofHours(1)));
        givenEvent();

        assertThatThrownBy(() -> eventService.publish(GROUP_ID, EVENT_ID, OFFICER_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void anEventCanBeCompletedOnlyAfterItStarted() {
        event.publish();
        givenEvent();

        assertThatThrownBy(() -> eventService.complete(GROUP_ID, EVENT_ID, OFFICER_ID))
                .isInstanceOf(BusinessRuleException.class);

        event.setStartsAt(NOW.minus(Duration.ofHours(1)));
        assertThat(eventService.complete(GROUP_ID, EVENT_ID, OFFICER_ID).status()).isEqualTo(EventStatus.COMPLETED);
    }

    private void givenEvent() {
        when(eventRepository.findByIdAndGroupId(EVENT_ID, GROUP_ID)).thenReturn(Optional.of(event));
    }

    private void givenLockedSlots() {
        when(slotRepository.findAllByEventIdForUpdate(EVENT_ID)).thenReturn(List.of(slot));
    }

    private void givenRole(Long userId, MembershipRole role) {
        User user = new User("u@example.com", "hash", "User", "it");
        when(membershipRepository.findByGroupIdAndUserId(GROUP_ID, userId))
                .thenReturn(Optional.of(new Membership(user, group, role)));
    }

    /** The database would assign ids on insert: do the same for events and their slots. */
    private void givenSavedEventsGetIds() {
        AtomicLong ids = new AtomicLong(1);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", ids.getAndIncrement());
            saved.getSlots().forEach(s -> ReflectionTestUtils.setField(s, "id", ids.getAndIncrement()));
            return saved;
        });
    }

    private static CreateEventRequest createRequest(Instant startsAt, Integer weeklyOccurrences, String timeZone,
            SlotRequest... slots) {
        return new CreateEventRequest("Raid", null, null, startsAt, 120, false, 0, List.of(slots), weeklyOccurrences,
                timeZone);
    }

    private static UpdateEventRequest updateRequest(Long version, SlotRequest... slots) {
        return new UpdateEventRequest("Raid night", null, null, NEXT_WEEK, 120, false, 0, List.of(slots), version);
    }

    private static SlotRequest slot(String roleName, int capacity) {
        return new SlotRequest(null, roleName, capacity);
    }

    private static SlotRequest slot(Long id, String roleName, int capacity) {
        return new SlotRequest(id, roleName, capacity);
    }
}
