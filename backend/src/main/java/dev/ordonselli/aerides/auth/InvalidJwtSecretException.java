package dev.ordonselli.aerides.auth;

/**
 * JWT_SECRET is missing or too short. The message never contains the secret: it ends up in the startup log.
 * {@link InvalidJwtSecretFailureAnalyzer} turns it into a short "APPLICATION FAILED TO START" report.
 */
public class InvalidJwtSecretException extends RuntimeException {

    public InvalidJwtSecretException(String message) {
        super(message);
    }
}
