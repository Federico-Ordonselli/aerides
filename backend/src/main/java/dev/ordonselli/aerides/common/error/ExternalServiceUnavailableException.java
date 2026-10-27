package dev.ordonselli.aerides.common.error;

/** A third-party service (e.g. RAWG) is down, slow or not configured. Maps to 503. */
public class ExternalServiceUnavailableException extends RuntimeException {

    public ExternalServiceUnavailableException(String message) {
        super(message);
    }

    public ExternalServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
