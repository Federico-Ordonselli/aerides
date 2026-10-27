package dev.ordonselli.aerides.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the real migrations as the owner with {@link AppRoleProvisioner}, then connects as the application role
 * to check what it can and can't do. No Spring context: only Flyway, the callback and PostgreSQL.
 */
@Testcontainers
class AppRoleProvisionerIT {

    private static final String ROLE = "aerides_app";
    private static final String PASSWORD = "app-password-0123456789";
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @BeforeEach
    void migrate() {
        migrateWithAppPassword(PASSWORD);
    }

    @Test
    void appRoleReadsAndWritesRows() throws SQLException {
        try (Connection app = connectAsApp(PASSWORD); Statement sql = app.createStatement()) {
            sql.execute("INSERT INTO users (email, password_hash, display_name) VALUES ('rw@example.com', 'x', 'Rw')");
            sql.execute("UPDATE users SET display_name = 'Rw2' WHERE email = 'rw@example.com'");
            assertThat(sql.executeQuery("SELECT display_name FROM users WHERE email = 'rw@example.com'").next()).isTrue();
            assertThat(sql.executeUpdate("DELETE FROM users WHERE email = 'rw@example.com'")).isEqualTo(1);
        }
    }

    @Test
    void appRoleCannotChangeTheSchema() throws SQLException {
        try (Connection app = connectAsApp(PASSWORD); Statement sql = app.createStatement()) {
            assertDenied(sql, "CREATE TABLE intruder (id INT)");
            assertDenied(sql, "DROP TABLE users");
            assertDenied(sql, "ALTER TABLE users ADD COLUMN is_admin BOOLEAN");
            assertDenied(sql, "TRUNCATE users");
        }
    }

    @Test
    void appRoleCannotReadOrFakeTheMigrationHistory() throws SQLException {
        try (Connection app = connectAsApp(PASSWORD); Statement sql = app.createStatement()) {
            assertDenied(sql, "SELECT * FROM flyway_schema_history");
            assertDenied(sql, "DELETE FROM flyway_schema_history");
        }
    }

    @Test
    void tablesOfLaterMigrationsAreGrantedOnTheNextRun() throws SQLException {
        try (Connection owner = connectAsOwner(); Statement sql = owner.createStatement()) {
            sql.execute("CREATE TABLE later_table (id INT)"); // stands in for a new migration
        }

        migrateWithAppPassword(PASSWORD);

        try (Connection app = connectAsApp(PASSWORD); Statement sql = app.createStatement()) {
            sql.execute("INSERT INTO later_table VALUES (1)");
        }
    }

    @Test
    void anotherRunWithANewPasswordRotatesIt() throws SQLException {
        migrateWithAppPassword("rotated-password-0123456789");

        assertThatThrownBy(() -> connectAsApp(PASSWORD)).isInstanceOf(SQLException.class);
        connectAsApp("rotated-password-0123456789").close();
    }

    @Test
    void clearTextModeSetsPasswordsWithQuotesSafely() throws SQLException {
        String tricky = "it's-a-pass'; DROP TABLE users; --";
        migrateWithAppPassword(tricky, false);

        connectAsApp(tricky).close();
        try (Connection owner = connectAsOwner(); Statement sql = owner.createStatement()) {
            ResultSet users = sql.executeQuery("SELECT to_regclass('users') IS NOT NULL");
            users.next();
            assertThat(users.getBoolean(1)).as("users table still there").isTrue();
        }
    }

    @Test
    void refusesTheOwnerAsApplicationRole() {
        assertThatThrownBy(() -> new AppRoleProvisioner(ROLE, ROLE, PASSWORD, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesRoleNamesThatAreNotPlainIdentifiers() {
        assertThatThrownBy(() -> new AppRoleProvisioner("owner", "app\" SUPERUSER --", PASSWORD, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void migrateWithAppPassword(String password) {
        migrateWithAppPassword(password, true);
    }

    private static void migrateWithAppPassword(String password, boolean hashPassword) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .callbacks(new AppRoleProvisioner(postgres.getUsername(), ROLE, password, hashPassword))
                .load()
                .migrate();
    }

    private static void assertDenied(Statement sql, String statement) {
        assertThatThrownBy(() -> sql.execute(statement))
                .as(statement)
                .isInstanceOfSatisfying(SQLException.class,
                        e -> assertThat(e.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    private static Connection connectAsApp(String password) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), ROLE, password);
    }

    private static Connection connectAsOwner() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
