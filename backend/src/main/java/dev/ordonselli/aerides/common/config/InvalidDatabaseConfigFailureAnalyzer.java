package dev.ordonselli.aerides.common.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Registered in META-INF/spring.factories: Spring Boot asks it to explain a failed startup. */
class InvalidDatabaseConfigFailureAnalyzer extends AbstractFailureAnalyzer<InvalidDatabaseConfigException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, InvalidDatabaseConfigException cause) {
        String description = "The database configuration is incomplete:\n\n    - "
                + String.join("\n    - ", cause.problems());
        String action = """
                Set the variables in the service's environment (on Render: Environment; with Terraform: apps.tf).
                DB_URL is a JDBC URL, e.g. jdbc:postgresql://<host>/<database>?sslmode=verify-full
                For a local run against localhost:5432, use the dev profile: SPRING_PROFILES_ACTIVE=dev""";
        return new FailureAnalysis(description, action, cause);
    }
}
