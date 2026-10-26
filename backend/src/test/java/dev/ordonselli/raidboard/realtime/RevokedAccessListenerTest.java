package dev.ordonselli.raidboard.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import dev.ordonselli.raidboard.group.GroupAccessRevokedEvent;

@ExtendWith(MockitoExtension.class)
class RevokedAccessListenerTest {

    private static final Long USER_ID = 7L;

    @Mock
    private SimpUserRegistry userRegistry;
    @Mock
    private MessageChannel clientOutboundChannel;

    private RevokedAccessListener listener;

    @BeforeEach
    void setUp() {
        listener = new RevokedAccessListener(userRegistry, clientOutboundChannel);
    }

    @Test
    void everySessionOfTheUserGetsAnErrorFrame() {
        Set<SimpSession> sessions = Set.of(session("tab-1"), session("tab-2"));
        SimpUser user = mock(SimpUser.class);
        when(user.getSessions()).thenReturn(sessions);
        when(userRegistry.getUser(USER_ID.toString())).thenReturn(user);

        listener.onAccessRevoked(new GroupAccessRevokedEvent(1L, USER_ID));

        ArgumentCaptor<Message<?>> sent = ArgumentCaptor.captor();
        verify(clientOutboundChannel, times(2)).send(sent.capture());
        assertThat(sent.getAllValues()).map(StompHeaderAccessor::wrap)
                .allSatisfy(headers -> {
                    assertThat(headers.getCommand()).isEqualTo(StompCommand.ERROR);
                    assertThat(headers.getMessage()).isEqualTo(RevokedAccessListener.ACCESS_REVOKED);
                })
                .map(StompHeaderAccessor::getSessionId)
                .containsExactlyInAnyOrder("tab-1", "tab-2");
    }

    @Test
    void aUserWithoutOpenSessionsIsIgnored() {
        listener.onAccessRevoked(new GroupAccessRevokedEvent(1L, USER_ID));

        verify(clientOutboundChannel, never()).send(any());
    }

    private static SimpSession session(String id) {
        SimpSession session = mock(SimpSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }
}
