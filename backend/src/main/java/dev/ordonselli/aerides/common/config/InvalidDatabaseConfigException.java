package dev.ordonselli.aerides.common.config;

import java.util.List;

/** The database settings are missing or malformed. The message names variables only, never their values. */
public class InvalidDatabaseConfigException extends RuntimeException {

    private final List<String> problems;

    public InvalidDatabaseConfigException(List<String> problems) {
        super("Invalid database configuration: " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
