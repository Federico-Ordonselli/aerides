package dev.ordonselli.raidboard.common.error;

/** The request is well-formed but violates a business rule, e.g. withdrawing after the deadline. Maps to 422. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
