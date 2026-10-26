package dev.ordonselli.raidboard.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import dev.ordonselli.raidboard.event.Event;
import dev.ordonselli.raidboard.group.Group;
import dev.ordonselli.raidboard.user.User;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2027-01-10T12:00:00Z");
    private static final String KEY = "reminder:event:10";

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private EmailComposer emailComposer;
    @Mock
    private EmailSender emailSender;

    private NotificationService service;
    private User user;
    private Event event;
    private final EmailMessage email = new EmailMessage("mario@example.com", "Subject", "<p>Hi</p>");

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, emailComposer, emailSender, JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User("mario@example.com", "hash", "Mario", "it");
        ReflectionTestUtils.setField(user, "id", 5L);
        Group group = new Group("Gilda", "gilda", null, "ABCDEFGHJK");
        ReflectionTestUtils.setField(group, "id", 1L);
        event = new Event(group, null, "Raid", NOW.plusSeconds(3600), 120);
        ReflectionTestUtils.setField(event, "id", 10L);
    }

    @Test
    void sendsAndMarksTheNotificationSent() {
        givenClaimed();
        when(emailComposer.compose(NotificationType.EVENT_REMINDER, user, event)).thenReturn(email);

        boolean sent = service.sendOnce(NotificationType.EVENT_REMINDER, user, event, KEY);

        assertThat(sent).isTrue();
        verify(notificationRepository).insertIfAbsent(5L, "EVENT_REMINDER", "{\"groupId\":1,\"eventId\":10}", KEY);
        verify(emailSender).send(email);
        verify(notificationRepository).updateStatus(42L, NotificationStatus.SENT, NOW);
    }

    @Test
    void doesNothingWhenTheKeyIsAlreadyTaken() {
        when(notificationRepository.insertIfAbsent(eq(5L), anyString(), anyString(), eq(KEY))).thenReturn(0);

        boolean sent = service.sendOnce(NotificationType.EVENT_REMINDER, user, event, KEY);

        assertThat(sent).isFalse();
        verifyNoInteractions(emailComposer, emailSender);
    }

    @Test
    void aMailServerErrorMarksTheNotificationFailedAndIsNotThrown() {
        givenClaimed();
        when(emailComposer.compose(NotificationType.EVENT_REMINDER, user, event)).thenReturn(email);
        doThrow(new MailSendException("connection refused")).when(emailSender).send(email);

        boolean sent = service.sendOnce(NotificationType.EVENT_REMINDER, user, event, KEY);

        assertThat(sent).isFalse();
        verify(notificationRepository).updateStatus(42L, NotificationStatus.FAILED, null);
        verify(notificationRepository, never()).updateStatus(any(), eq(NotificationStatus.SENT), any());
    }

    private void givenClaimed() {
        when(notificationRepository.insertIfAbsent(eq(5L), anyString(), anyString(), eq(KEY))).thenReturn(1);
        Notification notification = new Notification();
        ReflectionTestUtils.setField(notification, "id", 42L);
        when(notificationRepository.findByUserIdAndDedupKey(5L, KEY)).thenReturn(Optional.of(notification));
    }
}
