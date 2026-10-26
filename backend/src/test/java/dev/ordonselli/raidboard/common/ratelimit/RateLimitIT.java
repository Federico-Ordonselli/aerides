package dev.ordonselli.raidboard.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import dev.ordonselli.raidboard.support.AbstractIntegrationTest;

/**
 * Runs in its own Spring context with rate limiting enabled. Every test uses a different client IP,
 * so the tests don't consume each other's buckets.
 */
@TestPropertySource(properties = "raidboard.rate-limit.enabled=true")
class RateLimitIT extends AbstractIntegrationTest {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    private RateLimitProperties limits;

    @Test
    void tooManyLoginAttemptsForTheSameEmailReturn429WithRetryAfter() throws Exception {
        String ip = uniqueIp();
        int capacity = limits.loginPerIpAndEmail().capacity();

        for (int attempt = 0; attempt < capacity; attempt++) {
            // Changing the case must not give extra attempts
            String email = attempt % 2 == 0 ? "victim@example.com" : "VICTIM@example.com";
            mockMvc.perform(login(email).with(from(ip))).andExpect(status().isUnauthorized());
        }

        MvcResult rejected = mockMvc.perform(login("victim@example.com").with(from(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too many requests"))
                .andReturn();
        long retryAfter = Long.parseLong(rejected.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertThat(retryAfter).isBetween(1L, limits.loginPerIpAndEmail().period().toSeconds());
    }

    @Test
    void theLimitIsPerClientIp() throws Exception {
        String attackerIp = uniqueIp();
        for (int attempt = 0; attempt < limits.loginPerIpAndEmail().capacity(); attempt++) {
            mockMvc.perform(login("someone@example.com").with(from(attackerIp)));
        }
        mockMvc.perform(login("someone@example.com").with(from(attackerIp)))
                .andExpect(status().isTooManyRequests());

        // The real user, from another IP, can still try to log in
        mockMvc.perform(login("someone@example.com").with(from(uniqueIp())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void manyDifferentEmailsFromTheSameIpHitTheIpLimit() throws Exception {
        String ip = uniqueIp();
        int capacity = limits.loginPerIp().capacity();

        for (int attempt = 0; attempt < capacity; attempt++) {
            mockMvc.perform(login("user" + attempt + "@example.com").with(from(ip)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(login("one-more@example.com").with(from(ip)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void tooManyRefreshCallsReturn429() throws Exception {
        String ip = uniqueIp();
        int capacity = limits.refreshPerIp().capacity();

        for (int attempt = 0; attempt < capacity; attempt++) {
            mockMvc.perform(post("/api/auth/refresh").with(from(ip))).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/refresh").with(from(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank());
    }

    private static MockHttpServletRequestBuilder login(String email) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "wrong-password"}
                        """.formatted(email));
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    /** IPs from the 203.0.113.0/24 range, reserved for documentation and tests. */
    private static String uniqueIp() {
        return "203.0.113." + NEXT_IP.getAndIncrement();
    }
}
