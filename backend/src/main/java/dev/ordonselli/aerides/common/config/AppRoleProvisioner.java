package dev.ordonselli.aerides.common.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.postgresql.PGConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Least privilege for the database: Flyway connects as the schema owner, the application as a role that can
 * only read and write rows. Active when Flyway has its own user ({@code SPRING_FLYWAY_USER} and
 * {@code SPRING_FLYWAY_PASSWORD}); {@code DB_USERNAME} and {@code DB_PASSWORD} are then the application role,
 * which this callback creates, or updates, after every migration, as the owner.
 * <p>
 * After each run the role can SELECT, INSERT, UPDATE and DELETE on the application's tables and nothing else:
 * no DDL, and no access to Flyway's history table. A SQL injection through the application can't drop a table
 * or fake a migration. Granting again on every start covers the tables that a new migration creates.
 * Without a separate Flyway user (docker compose, tests) the callback doesn't exist and one user does both.
 * <p>
 * The password is hashed by the driver (SCRAM) by default, so it never travels or gets logged in clear text.
 * Some hosted PostgreSQL services (Neon) accept only clear-text passwords, to check their strength:
 * {@code DB_HASH_APP_PASSWORD=false} sends it in clear text, over the TLS connection.
 */
@Component
@ConditionalOnProperty("spring.flyway.user")
public class AppRoleProvisioner implements Callback {

    // The name is put into SQL as an identifier, which can't be a bind parameter: accept only plain names
    private static final Pattern ROLE_NAME = Pattern.compile("[a-z_][a-z0-9_]{0,62}");
    private static final String SCHEMA = "public"; // where the migrations create the tables

    private final String role;
    private final String password;
    private final boolean hashPassword;

    public AppRoleProvisioner(@Value("${spring.flyway.user}") String owner,
                              @Value("${spring.datasource.username}") String role,
                              @Value("${spring.datasource.password}") String password,
                              @Value("${aerides.database.hash-app-password:true}") boolean hashPassword) {
        if (!ROLE_NAME.matcher(role).matches()) {
            throw new IllegalArgumentException("Database user must be lowercase letters, digits and _: " + role);
        }
        // Same user twice would revoke the owner's own rights on Flyway's history table
        if (role.equals(owner)) {
            throw new IllegalArgumentException("SPRING_FLYWAY_USER and DB_USERNAME must be different users");
        }
        this.role = role;
        this.password = password;
        this.hashPassword = hashPassword;
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        Connection connection = context.getConnection();
        String historyTable = context.getConfiguration().getTable();
        try (Statement statement = connection.createStatement()) {
            if (!roleExists(connection)) {
                statement.execute("CREATE ROLE \"%s\" LOGIN".formatted(role));
            }
            // Set on every start, so changing the secret and restarting is enough to rotate it
            setPassword(connection);
            statement.execute("GRANT USAGE ON SCHEMA %s TO \"%s\"".formatted(SCHEMA, role));
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %s TO \"%s\""
                    .formatted(SCHEMA, role));
            statement.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %s TO \"%s\"".formatted(SCHEMA, role));
            statement.execute("REVOKE ALL ON %s.\"%s\" FROM \"%s\"".formatted(SCHEMA, historyTable, role));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not set up the database role " + role, e);
        }
    }

    @Override
    public String getCallbackName() {
        return "appRoleProvisioner";
    }

    private void setPassword(Connection connection) throws SQLException {
        if (hashPassword) {
            connection.unwrap(PGConnection.class).alterUserPassword(role, password.toCharArray(), "scram-sha-256");
            return;
        }
        // A password can't be a bind parameter in ALTER ROLE: format() lets the server quote it as a literal
        String alterRole;
        try (PreparedStatement query = connection.prepareStatement("SELECT format('ALTER ROLE %I PASSWORD %L', ?, ?)")) {
            query.setString(1, role);
            query.setString(2, password);
            try (ResultSet result = query.executeQuery()) {
                result.next();
                alterRole = result.getString(1);
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(alterRole);
        }
    }

    private boolean roleExists(Connection connection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname = ?")) {
            query.setString(1, role);
            try (ResultSet result = query.executeQuery()) {
                return result.next();
            }
        }
    }
}
