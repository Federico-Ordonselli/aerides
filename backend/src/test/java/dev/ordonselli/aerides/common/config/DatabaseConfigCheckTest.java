package dev.ordonselli.aerides.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

class DatabaseConfigCheckTest {

    private static final String PASSWORD = "s3cret-db-password";

    private final DatabaseConfigCheck check = new DatabaseConfigCheck();

    @Test
    void acceptsACompleteConfiguration() {
        assertThatCode(() -> check.postProcessEnvironment(complete(), null)).doesNotThrowAnyException();
    }

    @Test
    void namesEveryMissingVariable() {
        assertThatThrownBy(() -> check.postProcessEnvironment(new MockEnvironment(), null))
                .isInstanceOfSatisfying(InvalidDatabaseConfigException.class, e -> assertThat(e.problems())
                        .containsExactly("DB_URL is not set", "DB_USERNAME is not set", "DB_PASSWORD is not set"));
    }

    @Test
    void treatsBlankAsMissingAndNeverPrintsValues() {
        MockEnvironment environment = complete();
        environment.setProperty("spring.datasource.url", "   ");

        assertThatThrownBy(() -> check.postProcessEnvironment(environment, null))
                .isInstanceOfSatisfying(InvalidDatabaseConfigException.class,
                        e -> assertThat(e.problems()).containsExactly("DB_URL is not set"))
                .hasMessageNotContaining(PASSWORD);
    }

    @Test
    void refusesAConnectionStringThatIsNotJdbc() {
        MockEnvironment environment = complete();
        environment.setProperty("spring.datasource.url", "postgresql://owner:" + PASSWORD + "@db.example.com/app");

        assertThatThrownBy(() -> check.postProcessEnvironment(environment, null))
                .hasMessageContaining("DB_URL must start with jdbc:postgresql://")
                .hasMessageNotContaining(PASSWORD)
                .hasMessageNotContaining("db.example.com");
    }

    @Test
    void requiresTheFlywayPasswordOnlyWithASeparateFlywayUser() {
        MockEnvironment environment = complete();
        environment.setProperty("spring.flyway.user", "owner");

        assertThatThrownBy(() -> check.postProcessEnvironment(environment, null))
                .isInstanceOfSatisfying(InvalidDatabaseConfigException.class,
                        e -> assertThat(e.problems()).containsExactly("SPRING_FLYWAY_PASSWORD is not set"));

        environment.setProperty("spring.flyway.password", "owner-password");
        assertThatCode(() -> check.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test"})
    void skipsTheProfilesWithTheirOwnDatabase(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile, "demo");

        assertThatCode(() -> check.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    @Test
    void checksTheDemoProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("demo");

        assertThatThrownBy(() -> check.postProcessEnvironment(environment, null))
                .isInstanceOf(InvalidDatabaseConfigException.class);
    }

    @Test
    void startupReportListsTheProblemsAndWhatToDo() {
        InvalidDatabaseConfigException failure = new InvalidDatabaseConfigException(
                java.util.List.of("DB_URL is not set", "DB_PASSWORD is not set"));

        FailureAnalysis analysis = new InvalidDatabaseConfigFailureAnalyzer().analyze(new IllegalStateException(failure));

        assertThat(analysis.getDescription()).contains("- DB_URL is not set", "- DB_PASSWORD is not set");
        assertThat(analysis.getAction()).contains("jdbc:postgresql://", "SPRING_PROFILES_ACTIVE=dev");
    }

    @Test
    void checkAndAnalyzerAreRegisteredWithSpringBoot() {
        // Some of Spring Boot's own factories need constructor arguments that only a running application has: skip them
        SpringFactoriesLoader.FailureHandler skip = SpringFactoriesLoader.FailureHandler.handleMessage((message, e) -> { });
        SpringFactoriesLoader loader = SpringFactoriesLoader.forDefaultResourceLocation();

        assertThat(loader.load(EnvironmentPostProcessor.class, skip)).hasAtLeastOneElementOfType(DatabaseConfigCheck.class);
        assertThat(loader.load(FailureAnalyzer.class, skip))
                .hasAtLeastOneElementOfType(InvalidDatabaseConfigFailureAnalyzer.class);
    }

    private static MockEnvironment complete() {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.com/app?sslmode=verify-full")
                .withProperty("spring.datasource.username", "app")
                .withProperty("spring.datasource.password", PASSWORD);
    }
}
