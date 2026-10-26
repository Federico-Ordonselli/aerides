package dev.ordonselli.raidboard.common.error;

/** The requested resource does not exist (or the caller is not allowed to know it exists). Maps to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
