package dev.ordonselli.aerides.common.error;

/** The request conflicts with the current state, e.g. an email that is already registered. Maps to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
