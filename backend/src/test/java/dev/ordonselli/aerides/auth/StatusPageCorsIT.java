package dev.ordonselli.aerides.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import dev.ordonselli.aerides.support.AbstractIntegrationTest;

/**
 * The wake-up page on another origin may read the readiness probe, and nothing else.
 * The test profile sets aerides.security.status-page-origins to {@link #STATUS_PAGE}.
 */
class StatusPageCorsIT extends AbstractIntegrationTest {

    private static final String STATUS_PAGE = "https://status.example.org";
    private static final String READINESS = SecurityConfig.READINESS_PATH;

    @Test
    void statusPageCanReadOnlyTheReadinessStatus() throws Exception {
        mockMvc.perform(get(READINESS).header(HttpHeaders.ORIGIN, STATUS_PAGE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, STATUS_PAGE))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                // Only the status: no components, no details, no group names
                .andExpect(content().json("""
                        {"status": "UP"}
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void otherOriginsCannotReadTheReadinessProbe() throws Exception {
        mockMvc.perform(get(READINESS).header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void statusPageCannotUseOtherMethodsOnTheReadinessProbe() throws Exception {
        mockMvc.perform(preflight(READINESS, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusPageCannotSendExtraHeadersToTheReadinessProbe() throws Exception {
        mockMvc.perform(preflight(READINESS, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusPageCannotReadTheFullHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health").header(HttpHeaders.ORIGIN, STATUS_PAGE))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void statusPageCannotCallTheApi() throws Exception {
        mockMvc.perform(preflight("/api/groups", "GET"))
                .andExpect(status().isForbidden());
        mockMvc.perform(preflight("/api/auth/login", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void appOriginKeepsItsOwnCorsRules() throws Exception {
        mockMvc.perform(options("/api/groups")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    private static MockHttpServletRequestBuilder preflight(String path, String method) {
        return options(path)
                .header(HttpHeaders.ORIGIN, STATUS_PAGE)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method);
    }
}
