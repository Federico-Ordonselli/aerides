package dev.ordonselli.raidboard.common.error;

/** A request parameter is invalid in a way Bean Validation can't express, e.g. an unknown sort field. Maps to 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
