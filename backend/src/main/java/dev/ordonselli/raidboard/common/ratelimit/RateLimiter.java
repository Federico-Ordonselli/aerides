package dev.ordonselli.raidboard.common.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.stream.Stream;
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
 * Because the state is local to this JVM, each instance counts separately, and a flood of distinct keys
 * can push old buckets out of the cache (resetting them): with several instances or under attack,
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
        String client = clientKey(clientIp);
        // Normalized like AuthService does, so "Mario@x.com" and "mario@x.com" share the same bucket
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        consume("login-ip:" + client, properties.loginPerIp());
        consume("login-ip-email:" + client + ":" + normalizedEmail, properties.loginPerIpAndEmail());
        // Independent of the IP: stops a botnet spreading guesses for one account over many addresses.
        // Kept looser than the other limits, because an attacker can use it to block the real user.
        consume("login-email:" + normalizedEmail, properties.loginPerEmail());
    }

    public void checkRefresh(String clientIp) {
        consume("refresh-ip:" + clientKey(clientIp), properties.refreshPerIp());
    }

    /**
     * IPv4 addresses are used as they are. IPv6 addresses are reduced to their /64 prefix: a single
     * client usually controls a whole /64 (2^64 addresses), so limiting per address would be useless.
     */
    static String clientKey(String ip) {
        if (!ip.contains(":")) {
            return ip;
        }
        try {
            // For an IP literal this only parses the text, it never does a DNS lookup
            byte[] address = InetAddress.getByName(ip).getAddress();
            if (address.length != 16) {
                return ip;
            }
            return HexFormat.of().formatHex(address, 0, 8) + "::/64";
        } catch (UnknownHostException e) {
            return ip;
        }
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
        return Stream.of(properties.loginPerIpAndEmail(), properties.loginPerIp(), properties.loginPerEmail(),
                        properties.refreshPerIp())
                .map(Limit::period)
                .max(Duration::compareTo)
                .orElseThrow();
    }
}
