package dev.ordonselli.raidboard.auth;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ordonselli.raidboard.common.error.ConflictException;
import dev.ordonselli.raidboard.user.User;
import dev.ordonselli.raidboard.user.UserMapper;
import dev.ordonselli.raidboard.user.UserRepository;

@Service
public class AuthService {

    private static final String DEFAULT_LOCALE = "it";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    /** Compared against when the email is unknown, see {@link #login}. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-used-only-for-timing");
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
        String locale = request.locale() != null ? request.locale() : DEFAULT_LOCALE;
        User user = new User(email, passwordEncoder.encode(request.password()), request.displayName().trim(), locale);
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(normalizeEmail(request.email()));
        // Always run exactly one BCrypt comparison, even for unknown emails: otherwise those requests
        // would answer much faster and an attacker could find out which emails are registered.
        String passwordHash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        if (user.isEmpty() || !passwordMatches) {
            // Same message for both cases, for the same reason
            throw new BadCredentialsException("Invalid email or password");
        }
        return issueTokens(user.get());
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResult refresh(String rawRefreshToken) {
        IssuedRefreshToken refreshToken = refreshTokenService.rotate(rawRefreshToken);
        User user = refreshToken.user();
        return new AuthResult(jwtTokenService.createAccessToken(user), refreshToken, userMapper.toResponse(user));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private AuthResult issueTokens(User user) {
        return new AuthResult(
                jwtTokenService.createAccessToken(user),
                refreshTokenService.issue(user),
                userMapper.toResponse(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
