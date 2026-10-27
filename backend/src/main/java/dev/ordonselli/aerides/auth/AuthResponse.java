package dev.ordonselli.aerides.auth;

import java.time.Instant;

import dev.ordonselli.aerides.user.UserResponse;

/**
 * Body returned by login, register and refresh.
 * The refresh token is <b>not</b> here: it travels in an HttpOnly cookie that JavaScript cannot read.
 */
public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {

    public static AuthResponse bearer(AccessToken accessToken, UserResponse user) {
        return new AuthResponse(accessToken.value(), "Bearer", accessToken.expiresAt(), user);
    }
}
