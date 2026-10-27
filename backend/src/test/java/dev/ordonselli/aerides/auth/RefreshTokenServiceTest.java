package dev.ordonselli.aerides.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import dev.ordonselli.aerides.user.User;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-10T12:00:00Z");

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties("x".repeat(32), "aerides",
                Duration.ofMinutes(15), Duration.ofDays(14), true, List.of("http://localhost:4200"), List.of());
        service = new RefreshTokenService(refreshTokenRepository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User("mario@example.com", "hash", "Mario", "it");
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    @Test
    void issueStoresOnlyTheHashOfTheToken() {
        IssuedRefreshToken issued = service.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(RefreshTokenService.hash(issued.rawValue()))
                .isNotEqualTo(issued.rawValue());
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
    }

    @Test
    void rotateRevokesTheOldTokenAndIssuesANewOne() {
        RefreshToken current = new RefreshToken(user, RefreshTokenService.hash("old"), NOW.plusSeconds(60));
        when(refreshTokenRepository.findByTokenHashForUpdate(RefreshTokenService.hash("old")))
                .thenReturn(Optional.of(current));

        IssuedRefreshToken rotated = service.rotate("old");

        assertThat(current.isRevoked()).isTrue();
        assertThat(rotated.rawValue()).isNotEqualTo("old");
        assertThat(rotated.user()).isSameAs(user);
    }

    @Test
    void rotateWithRevokedTokenRevokesAllSessionsOfTheUser() {
        RefreshToken stolen = new RefreshToken(user, RefreshTokenService.hash("stolen"), NOW.plusSeconds(60));
        stolen.revoke();
        when(refreshTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(stolen));

        assertThatThrownBy(() -> service.rotate("stolen")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository).revokeAllForUser(7L);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateWithExpiredTokenFails() {
        RefreshToken expired = new RefreshToken(user, RefreshTokenService.hash("expired"), NOW);
        when(refreshTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("expired"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh token expired");
    }

    @Test
    void rotateWithUnknownTokenFails() {
        when(refreshTokenRepository.findByTokenHashForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("unknown")).isInstanceOf(InvalidRefreshTokenException.class);
    }
}
