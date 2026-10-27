package dev.ordonselli.aerides.auth;

import org.springframework.security.core.AuthenticationException;

/** The refresh token is missing, unknown, expired or revoked. Handled as a 401 like any authentication failure. */
public class InvalidRefreshTokenException extends AuthenticationException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
