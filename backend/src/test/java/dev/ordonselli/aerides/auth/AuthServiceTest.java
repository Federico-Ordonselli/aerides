package dev.ordonselli.aerides.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import dev.ordonselli.aerides.common.error.ConflictException;
import dev.ordonselli.aerides.user.User;
import dev.ordonselli.aerides.user.UserMapper;
import dev.ordonselli.aerides.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // The dummy hash is produced by the injected encoder: same algorithm and cost factor as real hashes
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-bcrypt-hash");
        authService = new AuthService(userRepository, userMapper, passwordEncoder, jwtTokenService,
                refreshTokenService);
    }

    @Test
    void registerNormalizesEmailHashesPasswordAndDefaultsLocale() {
        when(userRepository.existsByEmail("mario@example.com")).thenReturn(false);
        when(passwordEncoder.encode("supersecret")).thenReturn("bcrypt-hash");

        authService.register(new RegisterRequest("  Mario@Example.COM ", "supersecret", " Mario ", null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("mario@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Mario");
        assertThat(saved.getValue().getLocale()).isEqualTo("it");
        verify(refreshTokenService).issue(saved.getValue());
    }

    @Test
    void registerWithExistingEmailFails() {
        when(userRepository.existsByEmail("mario@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("mario@example.com", "supersecret", "Mario", "en")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithWrongPasswordFails() {
        User user = new User("mario@example.com", "bcrypt-hash", "Mario", "it");
        when(userRepository.findByEmail("mario@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "bcrypt-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("mario@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void loginWithUnknownEmailFailsWithTheSameMessage() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "whatever")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
        // A BCrypt comparison against the dummy hash still runs, so the response time
        // doesn't reveal that the email is unknown. We check the code path, not the clock.
        verify(passwordEncoder).matches("whatever", "dummy-bcrypt-hash");
    }

    @Test
    void loginWithCorrectCredentialsIssuesBothTokens() {
        User user = new User("mario@example.com", "bcrypt-hash", "Mario", "it");
        when(userRepository.findByEmail("mario@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("supersecret", "bcrypt-hash")).thenReturn(true);

        authService.login(new LoginRequest("Mario@example.com", "supersecret"));

        verify(jwtTokenService).createAccessToken(user);
        verify(refreshTokenService).issue(user);
    }
}
