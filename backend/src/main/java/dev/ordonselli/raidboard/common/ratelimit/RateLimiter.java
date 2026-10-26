package dev.ordonselli.raidboard.common.ratelimit;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import dev.ordonselli.raidboard.common.ratelimit.RateLimitProperties.Limit;

/**
 * In-memory rate limiting with the token-bucket algorithm (Bucket4j).
 * <p>
 * Each key (for example "login-ip:203.0.113.5") gets its own bucket of {@code capacity} tokens,
 * refilled every {@code period}. A request takes one token; with no tokens left it is rejected.
 * <p>
 * Buckets live in a bounded Caffeine cache and expire when unused, so memory cannot grow forever.
 * Because the state is local to this JVM, each instance counts separately: with several instances
 * the limits should move to a shared store (Redis) or to the API gateway.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimiter {

    private static final int MAX_TRACKED_KEYS = 100_000;

    private final RateLimitProperties properties;
    private final Cache<String, Bucket> buckets;

    public RateLimiter(RateLimitProperties properties) {
        this.properties = properties;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                // An unused bucket is full again after one period, so forgetting it changes nothing
                .expireAfterAccess(longestPeriod(properties))
                .build();
    }

    public void checkLogin(String clientIp, String email) {
        consume("login-ip:" + clientIp, properties.loginPerIp());
        // Normalized like AuthService does, so "Mario@x.com" and "mario@x.com" share the same bucket
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        consume("login-ip-email:" + clientIp + ":" + normalizedEmail, properties.loginPerIpAndEmail());
    }

    public void checkRefresh(String clientIp) {
        consume("refresh-ip:" + clientIp, properties.refreshPerIp());
    }

    private void consume(String key, Limit limit) {
        if (!properties.enabled()) {
            return;
        }
        Bucket bucket = buckets.get(key, ignored -> newBucket(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            // Round up: waiting 0.4 seconds must become "Retry-After: 1", not 0
            long retryAfterSeconds = Math.max(1,
                    TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() + 999_999_999L));
            throw new RateLimitExceededException(retryAfterSeconds);
        }
    }

    private static Bucket newBucket(Limit limit) {
        return Bucket.builder()
                .addLimit(bandwidth -> bandwidth
                        .capacity(limit.capacity())
                        .refillIntervally(limit.capacity(), limit.period()))
                .build();
    }

    private static Duration longestPeriod(RateLimitProperties properties) {
        Duration longest = properties.loginPerIpAndEmail().period();
        if (properties.loginPerIp().period().compareTo(longest) > 0) {
            longest = properties.loginPerIp().period();
        }
        if (properties.refreshPerIp().period().compareTo(longest) > 0) {
            longest = properties.refreshPerIp().period();
        }
        return longest;
    }
}
