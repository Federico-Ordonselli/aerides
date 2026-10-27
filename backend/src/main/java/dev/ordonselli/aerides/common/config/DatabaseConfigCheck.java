package dev.ordonselli.aerides.common.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * Stops the startup when the database settings are missing, before any connection is attempted. Without it, a
 * deployment that forgot DB_URL would try some default host and fail with a confusing connection error.
 * <p>
 * Runs while the environment is prepared, so before the DataSource and Flyway exist. Registered in
 * META-INF/spring.factories; {@link InvalidDatabaseConfigFailureAnalyzer} reports the problem. The report names
 * the variables, never their values. Skipped in the dev and test profiles: dev has localhost defaults
 * (application-dev.yml) and the tests get their database from Testcontainers.
 */
public class DatabaseConfigCheck implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.matchesProfiles("dev", "test")) {
            return;
        }
        List<String> problems = new ArrayList<>();
        String url = environment.getProperty("spring.datasource.url");
        if (!StringUtils.hasText(url)) {
            problems.add("DB_URL is not set");
        } else if (!url.startsWith("jdbc:postgresql://")) {
            problems.add("DB_URL must start with jdbc:postgresql:// (a JDBC URL, not postgresql://user:password@...)");
        }
        requireText(environment, "spring.datasource.username", "DB_USERNAME", problems);
        requireText(environment, "spring.datasource.password", "DB_PASSWORD", problems);
        // Only with a separate migration user (AppRoleProvisioner); otherwise Flyway uses DB_USERNAME/DB_PASSWORD
        if (StringUtils.hasText(environment.getProperty("spring.flyway.user"))) {
            requireText(environment, "spring.flyway.password", "SPRING_FLYWAY_PASSWORD", problems);
        }
        if (!problems.isEmpty()) {
            throw new InvalidDatabaseConfigException(problems);
        }
    }

    /** After the config files are read: the profiles and the ${...} defaults in application.yml are known by then. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static void requireText(ConfigurableEnvironment environment, String property, String variable,
            List<String> problems) {
        if (!StringUtils.hasText(environment.getProperty(property))) {
            problems.add(variable + " is not set");
        }
    }
}
