package dev.ordonselli.raidboard.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.core.io.support.SpringFactoriesLoader;

class JwtSecretCheckTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void refusesAMissingSecret(String secret) {
        assertThatThrownBy(() -> SecurityConfig.signingKey(secret))
                .isInstanceOf(InvalidJwtSecretException.class)
                .hasMessageContaining("JWT_SECRET is not set");
    }

    @Test
    void refusesAShortSecretWithoutPrintingIt() {
        String secret = "my-real-secret-but-31-bytes-xyz";
        assertThat(secret).hasSize(31);

        assertThatThrownBy(() -> SecurityConfig.signingKey(secret))
                .isInstanceOf(InvalidJwtSecretException.class)
                .hasMessageContaining("too short")
                .hasMessageNotContaining(secret);
    }

    @Test
    void countsBytesNotCharacters() {
        // 11 characters, 33 bytes in UTF-8: long enough for HS256
        assertThat(SecurityConfig.signingKey("€".repeat(11)).getEncoded()).hasSize(33);
        assertThat(SecurityConfig.signingKey("x".repeat(32)).getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    void startupReportExplainsWhatToDo() {
        InvalidJwtSecretException failure = new InvalidJwtSecretException("JWT_SECRET is not set.");

        FailureAnalysis analysis = new InvalidJwtSecretFailureAnalyzer().analyze(new IllegalStateException(failure));

        assertThat(analysis.getDescription()).isEqualTo("JWT_SECRET is not set.");
        assertThat(analysis.getAction()).contains("openssl rand");
    }

    @Test
    void analyzerIsRegisteredWithSpringBoot() {
        // Some of Spring Boot's own analyzers need constructor arguments that only a running application has: skip them
        SpringFactoriesLoader.FailureHandler skip = SpringFactoriesLoader.FailureHandler.handleMessage((message, e) -> { });

        assertThat(SpringFactoriesLoader.forDefaultResourceLocation().load(FailureAnalyzer.class, skip))
                .hasAtLeastOneElementOfType(InvalidJwtSecretFailureAnalyzer.class);
    }
}
