package dev.ordonselli.aerides.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void ipv4AddressesAreUsedAsTheyAre() {
        assertThat(RateLimiter.clientKey("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void ipv6AddressesInTheSameSlash64ShareTheSameKey() {
        String first = RateLimiter.clientKey("2001:db8:1234:5678::1");
        String second = RateLimiter.clientKey("2001:0db8:1234:5678:ffff:eeee:dddd:cccc");

        assertThat(first).isEqualTo(second).isEqualTo("20010db812345678::/64");
    }

    @Test
    void ipv6AddressesInDifferentSlash64HaveDifferentKeys() {
        assertThat(RateLimiter.clientKey("2001:db8:1234:5678::1"))
                .isNotEqualTo(RateLimiter.clientKey("2001:db8:1234:5679::1"));
    }
}
