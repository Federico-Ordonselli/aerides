package dev.ordonselli.aerides.realtime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpiredSessionSweeperTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    @Mock
    private StompSessionCloser sessionCloser;

    private final TokenExpiryRegistry tokenExpiryRegistry = new TokenExpiryRegistry();
    private ExpiredSessionSweeper sweeper;

    @BeforeEach
    void setUp() {
        sweeper = new ExpiredSessionSweeper(tokenExpiryRegistry, sessionCloser, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void onlySessionsWhoseTokenExpiredAreClosed() {
        tokenExpiryRegistry.register("expired", NOW.minusSeconds(1));
        tokenExpiryRegistry.register("expires-now", NOW);
        tokenExpiryRegistry.register("valid", NOW.plusSeconds(1));

        sweeper.closeExpiredSessions();

        verify(sessionCloser).close("expired", StompAuthInterceptor.AUTHENTICATION_REQUIRED);
        verify(sessionCloser).close("expires-now", StompAuthInterceptor.AUTHENTICATION_REQUIRED);
        verifyNoMoreInteractions(sessionCloser);
    }

    @Test
    void aClosedSessionIsNotClosedAgainByTheNextSweep() {
        tokenExpiryRegistry.register("expired", NOW.minusSeconds(1));

        sweeper.closeExpiredSessions();
        sweeper.closeExpiredSessions();

        verify(sessionCloser).close("expired", StompAuthInterceptor.AUTHENTICATION_REQUIRED);
        verifyNoMoreInteractions(sessionCloser);
    }
}
