package dev.ordonselli.raidboard.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ordonselli.raidboard.user.User;

/**
 * Refresh tokens are long random strings (not JWTs). The database keeps only their SHA-256 hash,
 * so a database leak does not expose usable tokens.
 * <p>
 * Every refresh <b>rotates</b> the token: the old one is revoked and a new one is issued.
 * If a revoked token is presented again, someone is replaying a stolen token, so all the user's
 * tokens are revoked and both the attacker and the user must log in again.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, SecurityProperties properties,
            Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken issue(User user) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
        refreshTokenRepository.save(new RefreshToken(user, hash(rawValue), expiresAt));
        return new IssuedRefreshToken(rawValue, expiresAt, user);
    }

    /**
     * Revokes the given token and issues a new one for the same user.
     * <p>
     * {@code noRollbackFor}: when reuse is detected we revoke every token of the user and then throw.
     * Without it, the exception would roll back the revocation we just did.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedRefreshToken rotate(String rawValue) {
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(hash(rawValue))
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));

        if (current.isRevoked()) {
            log.warn("Refresh token reuse detected for user {}: revoking all sessions", current.getUser().getId());
            refreshTokenRepository.revokeAllForUser(current.getUser().getId());
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }
        if (current.isExpired(clock.instant())) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }

        current.revoke();
        return issue(current.getUser());
    }

    /** Logout: revokes the token if it exists. Unknown tokens are ignored, logout always succeeds. */
    @Transactional
    public void revoke(String rawValue) {
        refreshTokenRepository.findByTokenHash(hash(rawValue)).ifPresent(RefreshToken::revoke);
    }

    static String hash(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Every Java runtime is required to support SHA-256
            throw new IllegalStateException(e);
        }
    }
}
