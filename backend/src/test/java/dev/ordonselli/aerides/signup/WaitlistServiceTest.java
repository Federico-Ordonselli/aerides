package dev.ordonselli.aerides.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import dev.ordonselli.aerides.event.Event;
import dev.ordonselli.aerides.event.EventSlot;
import dev.ordonselli.aerides.group.Group;
import dev.ordonselli.aerides.user.User;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    private static final Long SLOT_ID = 100L;

    @Mock
    private SignupRepository signupRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WaitlistService waitlistService;
    private Event event;
    private EventSlot slot;

    @BeforeEach
    void setUp() {
        waitlistService = new WaitlistService(signupRepository, eventPublisher);
        Group group = new Group("Gilda", "gilda", null, "ABCDEFGHJK");
        ReflectionTestUtils.setField(group, "id", 1L);
        event = new Event(group, null, "Raid", Instant.parse("2027-01-12T20:00:00Z"), 120);
        ReflectionTestUtils.setField(event, "id", 10L);
        slot = event.addSlot("Tank", 3);
        ReflectionTestUtils.setField(slot, "id", SLOT_ID);
    }

    @Test
    void promotesAsManyWaitlistedUsersAsThereAreFreeSeatsInFifoOrder() {
        // Capacity 3, one confirmed: two free seats
        when(signupRepository.countBySlotIdAndStatus(SLOT_ID, SignupStatus.CONFIRMED)).thenReturn(1L);
        Signup first = waitlisted(1L, 21L);
        Signup second = waitlisted(2L, 22L);
        when(signupRepository.findAllBySlotIdAndStatusOrderByPositionAsc(SLOT_ID, SignupStatus.WAITLISTED,
                Limit.of(2))).thenReturn(List.of(first, second));

        List<Signup> promoted = waitlistService.promote(slot);

        assertThat(promoted).containsExactly(first, second);
        assertThat(promoted).allMatch(signup -> signup.getStatus() == SignupStatus.CONFIRMED);
        verify(eventPublisher).publishEvent(new SignupPromotedEvent(1L, 10L, "Raid", 1L, 21L));
        verify(eventPublisher).publishEvent(new SignupPromotedEvent(1L, 10L, "Raid", 2L, 22L));
    }

    @Test
    void doesNothingWhenTheSlotIsFull() {
        when(signupRepository.countBySlotIdAndStatus(SLOT_ID, SignupStatus.CONFIRMED)).thenReturn(3L);

        assertThat(waitlistService.promote(slot)).isEmpty();
        verify(signupRepository, never()).findAllBySlotIdAndStatusOrderByPositionAsc(any(), any(), any());
        verifyNoInteractions(eventPublisher);
    }

    private Signup waitlisted(Long signupId, Long userId) {
        User user = new User("user" + userId + "@example.com", "hash", "User " + userId, "it");
        ReflectionTestUtils.setField(user, "id", userId);
        Signup signup = new Signup(event, slot, user, SignupStatus.WAITLISTED, signupId.intValue());
        ReflectionTestUtils.setField(signup, "id", signupId);
        return signup;
    }
}
