package dev.ordonselli.aerides.auth;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Registered in META-INF/spring.factories: Spring Boot asks it to explain a failed startup. */
class InvalidJwtSecretFailureAnalyzer extends AbstractFailureAnalyzer<InvalidJwtSecretException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, InvalidJwtSecretException cause) {
        return new FailureAnalysis(cause.getMessage(),
                "Set JWT_SECRET to a random value of at least 32 bytes, for example: openssl rand -base64 48",
                cause);
    }
}
