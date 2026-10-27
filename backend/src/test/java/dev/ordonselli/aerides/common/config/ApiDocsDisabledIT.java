package dev.ordonselli.aerides.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.ordonselli.aerides.support.AbstractIntegrationTest;

/**
 * Without a profile that opts in (dev, demo) the API docs don't exist: the paths are public in
 * SecurityConfig, so nothing but the springdoc switches keeps them closed.
 * {@code DemoProfileSecurityIT} checks the other side, the demo publishing them.
 */
class ApiDocsDisabledIT extends AbstractIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html"})
    void apiDocsAreOffByDefault(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound());
    }
}
