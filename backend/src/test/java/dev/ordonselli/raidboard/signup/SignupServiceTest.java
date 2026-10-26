package dev.ordonselli.raidboard.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import dev.ordonselli.raidboard.audit.AuditAction;
import dev.ordonselli.raidboard.audit.AuditEntityType;
import dev.ordonselli.raidboard.audit.AuditService;
import dev.ordonselli.raidboard.common.error.BusinessRuleException;
import dev.ordonselli.raidboard.common.error.ConflictException;
import dev.ordonselli.raidboard.common.error.NotFoundException;
import dev.ordonselli.raidboard.event.Event;
import dev.ordonselli.raidboard.event.EventRepository;
import dev.ordonselli.raidboard.event.EventSlot;
import dev.ordonselli.raidboard.event.EventSlotRepository;
import dev.ordonselli.raidboard.group.Group;
import dev.ordonselli.raidboard.user.User;
import dev.ordonselli.raidboard.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    private static final Long GROUP_ID = 1L;
    private static final Long EVENT_ID = 10L;
    private static final Long SLOT_ID = 100L;
    private static final Long USER_ID = 5L;
    private static final Long OFFICER_ID = 6L;
    private static final Instant NOW = Instant.parse("2027-01-10T12:00:00Z");

    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventSlotRepository slotRepository;
    @Mock
    private SignupRepository signupRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WaitlistService waitlistService;
    @Mock
    private AuditService auditService;

    private SignupService signupService;
    private Event event;
    private EventSlot slot;
    private User user;

    @BeforeEach
    void setUp() {
        signupService = new SignupService(eventRepository, slotRepository, signupRepository, userRepository,
                waitlistService, new SignupMapperImpl(), auditService, Clock.fixed(NOW, ZoneOffset.UTC));
        Group group = new Group("Gilda", "gilda", null, "ABCDEFGHJK");
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        // Starts in two days, withdrawals close 24 hours before
        event = new Event(group, null, "Raid", NOW.plus(Duration.ofDays(2)), 120);
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        event.setWithdrawDeadlineHours(24);
        event.publish();
        slot = event.addSlot("Tank", 2);
        ReflectionTestUtils.setField(slot, "id", SLOT_ID);
        user = new User("mario@example.com", "hash", "Mario", "it");
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    @Test
    void signUpConfirmsWhenASeatIsFree() {
        givenSignupPossible();
        when(waitlistService.freeSeats(slot)).thenReturn(1L);
        when(signupRepository.findMaxPositionBySlotId(SLOT_ID)).thenReturn(4);

        SignupResponse response = signupService.signUp(GROUP_ID, EVENT_ID, USER_ID, SLOT_ID);

        assertThat(response.status()).isEqualTo(SignupStatus.CONFIRMED);
        assertThat(response.position()).isEqualTo(5);
    }

    @Test
    void signUpGoesToTheWaitlistWhenTheSlotIsFull() {
        givenSignupPossible();
        when(waitlistService.freeSeats(slot)).thenReturn(0L);

        SignupResponse response = signupService.signUp(GROUP_ID, EVENT_ID, USER_ID, SLOT_ID);

        assertThat(response.status()).isEqualTo(SignupStatus.WAITLISTED);
    }

    @Test
    void signUpIsPendingWhenTheEventRequiresApproval() {
        event.setRequiresApproval(true);
        givenSignupPossible();

        SignupResponse response = signupService.signUp(GROUP_ID, EVENT_ID, USER_ID, SLOT_ID);

        assertThat(response.status()).isEqualTo(SignupStatus.PENDING);
        verify(waitlistService, never()).freeSeats(any());
    }

    @Test
    void signUpLocksTheSlotBeforeCountingSeats() {
        givenSignupPossible();
        when(waitlistService.freeSeats(slot)).thenReturn(1L);

        signupService.signUp(GROUP_ID, EVENT_ID, USER_ID, SLOT_ID);

        InOrder order = inOrder(slotRepository, waitlistService);
        order.verify(slotRepository).findByIdAndEventIdForUpdate(SLOT_ID, EVENT_ID);
        order.verify(waitlistService).freeSeats(slot);
    }

    @Test
    void aSecondActiveSignupIsRejected() {
        givenEvent();
        when(slotRepository.findByIdAndEventIdForUpdate(SLOT_ID, EVENT_ID)).thenReturn(Optional.of(slot));
        when(signupRepository.existsByEventIdAndUserIdAndStatusIn(EVENT_ID, USER_ID, SignupStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> signupService.signUp(GROUP_ID, EVENT_ID, USER_ID, SLOT_ID))
                .isInstanceOf(ConflictException.class);
        verify(signupRepository, never()).save(any());
    }

    @Test
    void signUpIsClosedOnceTheEventHasStarted() {
        event.setStartsAt(NOW.minus(Duration.ofMinutes(1)));
        givenEvent();

        assertThatThrownBy(() -> signupService.signUp(GROUP_ID, EVENT_ID, USER_ID, SLOT_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void signUpIsClosedForACancelledEvent() {
        event.cancel();
        givenEvent();

        assertThatThrownBy(() -> signupService.signUp(GROUP_ID, EVENT_ID, USER_ID, SLOT_ID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aDraftDoesNotExistForSignups() {
        Event draft = new Event(event.getGroup(), null, "Draft", NOW.plus(Duration.ofDays(2)), 60);
        when(eventRepository.findByIdAndGroupId(EVENT_ID, GROUP_ID)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> signupService.signUp(GROUP_ID, EVENT_ID, USER_ID, SLOT_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aConfirmedWithdrawalPromotesTheWaitlist() {
        Signup signup = givenActiveSignup(SignupStatus.CONFIRMED);

        signupService.withdraw(GROUP_ID, EVENT_ID, USER_ID);

        assertThat(signup.getStatus()).isEqualTo(SignupStatus.WITHDRAWN);
        verify(waitlistService).promote(slot);
    }

    @Test
    void aWaitlistedWithdrawalFreesNoSeat() {
        Signup signup = givenActiveSignup(SignupStatus.WAITLISTED);

        signupService.withdraw(GROUP_ID, EVENT_ID, USER_ID);

        assertThat(signup.getStatus()).isEqualTo(SignupStatus.WITHDRAWN);
        verify(waitlistService, never()).promote(any());
    }

    @Test
    void confirmedUsersCannotWithdrawAfterTheDeadline() {
        // 23 hours before the start: the 24-hour deadline has passed
        event.setStartsAt(NOW.plus(Duration.ofHours(23)));
        Signup signup = givenActiveSignup(SignupStatus.CONFIRMED);

        assertThatThrownBy(() -> signupService.withdraw(GROUP_ID, EVENT_ID, USER_ID))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(signup.getStatus()).isEqualTo(SignupStatus.CONFIRMED);
        verify(waitlistService, never()).promote(any());
    }

    @Test
    void confirmedUsersCanStillWithdrawExactlyAtTheDeadline() {
        event.setStartsAt(NOW.plus(Duration.ofHours(24)));
        Signup signup = givenActiveSignup(SignupStatus.CONFIRMED);

        signupService.withdraw(GROUP_ID, EVENT_ID, USER_ID);

        assertThat(signup.getStatus()).isEqualTo(SignupStatus.WITHDRAWN);
    }

    @Test
    void waitlistedUsersCanWithdrawAfterTheDeadline() {
        event.setStartsAt(NOW.plus(Duration.ofHours(1)));
        Signup signup = givenActiveSignup(SignupStatus.WAITLISTED);

        signupService.withdraw(GROUP_ID, EVENT_ID, USER_ID);

        assertThat(signup.getStatus()).isEqualTo(SignupStatus.WITHDRAWN);
    }

    @Test
    void withdrawLocksTheSlotBeforeReadingTheSignup() {
        givenActiveSignup(SignupStatus.WAITLISTED);

        signupService.withdraw(GROUP_ID, EVENT_ID, USER_ID);

        InOrder order = inOrder(slotRepository, signupRepository);
        order.verify(slotRepository).findByIdAndEventIdForUpdate(SLOT_ID, EVENT_ID);
        order.verify(signupRepository).findByEventIdAndUserIdAndStatusIn(EVENT_ID, USER_ID, SignupStatus.ACTIVE);
    }

    @Test
    void approvalGivesASeatWhenOneIsFreeAndIsAudited() {
        Signup signup = givenSignupToReview(SignupStatus.PENDING);
        when(waitlistService.freeSeats(slot)).thenReturn(1L);

        SignupResponse response = signupService.approve(GROUP_ID, EVENT_ID, signup.getId(), OFFICER_ID);

        assertThat(response.status()).isEqualTo(SignupStatus.CONFIRMED);
        verify(auditService).record(eq(GROUP_ID), eq(OFFICER_ID), eq(AuditAction.SIGNUP_APPROVED),
                eq(AuditEntityType.SIGNUP), eq(signup.getId()), anyMap());
    }

    @Test
    void approvalWaitlistsWhenTheSlotIsFull() {
        Signup signup = givenSignupToReview(SignupStatus.PENDING);
        when(waitlistService.freeSeats(slot)).thenReturn(0L);

        SignupResponse response = signupService.approve(GROUP_ID, EVENT_ID, signup.getId(), OFFICER_ID);

        assertThat(response.status()).isEqualTo(SignupStatus.WAITLISTED);
    }

    @Test
    void onlyPendingSignupsCanBeApproved() {
        Signup signup = givenSignupToReview(SignupStatus.CONFIRMED);

        assertThatThrownBy(() -> signupService.approve(GROUP_ID, EVENT_ID, signup.getId(), OFFICER_ID))
                .isInstanceOf(ConflictException.class);
        verify(auditService, never()).record(any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void rejectionIsAudited() {
        Signup signup = givenSignupToReview(SignupStatus.PENDING);

        SignupResponse response = signupService.reject(GROUP_ID, EVENT_ID, signup.getId(), OFFICER_ID);

        assertThat(response.status()).isEqualTo(SignupStatus.REJECTED);
        verify(auditService).record(eq(GROUP_ID), eq(OFFICER_ID), eq(AuditAction.SIGNUP_REJECTED),
                eq(AuditEntityType.SIGNUP), eq(signup.getId()), anyMap());
    }

    private void givenEvent() {
        when(eventRepository.findByIdAndGroupId(EVENT_ID, GROUP_ID)).thenReturn(Optional.of(event));
    }

    private void givenSignupPossible() {
        givenEvent();
        when(slotRepository.findByIdAndEventIdForUpdate(SLOT_ID, EVENT_ID)).thenReturn(Optional.of(slot));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(signupRepository.save(any(Signup.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Signup givenActiveSignup(SignupStatus status) {
        givenEvent();
        Signup signup = new Signup(event, slot, user, status, 1);
        when(signupRepository.findSlotIdByEventIdAndUserIdAndStatusIn(EVENT_ID, USER_ID, SignupStatus.ACTIVE))
                .thenReturn(Optional.of(SLOT_ID));
        when(slotRepository.findByIdAndEventIdForUpdate(SLOT_ID, EVENT_ID)).thenReturn(Optional.of(slot));
        when(signupRepository.findByEventIdAndUserIdAndStatusIn(EVENT_ID, USER_ID, SignupStatus.ACTIVE))
                .thenReturn(Optional.of(signup));
        return signup;
    }

    private Signup givenSignupToReview(SignupStatus status) {
        givenEvent();
        Signup signup = new Signup(event, slot, user, status, 1);
        ReflectionTestUtils.setField(signup, "id", 1000L);
        when(signupRepository.findSlotIdByIdAndEventId(1000L, EVENT_ID)).thenReturn(Optional.of(SLOT_ID));
        when(slotRepository.findByIdAndEventIdForUpdate(SLOT_ID, EVENT_ID)).thenReturn(Optional.of(slot));
        when(signupRepository.findByIdAndEventId(1000L, EVENT_ID)).thenReturn(Optional.of(signup));
        return signup;
    }
}
