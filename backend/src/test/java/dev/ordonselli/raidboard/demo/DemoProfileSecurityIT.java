package dev.ordonselli.raidboard.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import dev.ordonselli.raidboard.support.TestMailConfiguration;
import dev.ordonselli.raidboard.support.TestcontainersConfiguration;

/**
 * The demo profile adds a public security chain for the Angular files. Its own Spring context on
 * purpose: the other integration tests must run without that chain.
 * <p>
 * Real HTTP requests, not MockMvc: the point is how Tomcat decodes the path before Spring routes it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "RENDER_EXTERNAL_URL=http://localhost")
@ActiveProfiles({"test", "demo"})
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class DemoProfileSecurityIT {

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    private int port;

    /** %61 is "a": Spring MVC decodes it and routes to /api/..., so the JWT chain must apply too. */
    @ParameterizedTest
    @ValueSource(strings = {"/api/users/me", "/%61pi/users/me", "/%61pi/groups", "/API/../api/groups"})
    void apiPathsNeedATokenHoweverTheyAreWritten(String path) throws Exception {
        assertThat(get(path).statusCode()).isIn(400, 401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/groups/1/events/5", "/main-ABC123.js"})
    void frontendPathsArePublic(String path) throws Exception {
        // No Angular build in the test classpath: a 404 (no file) proves the request got past security
        assertThat(get(path).statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
