package dev.ordonselli.raidboard.auth;

import dev.ordonselli.raidboard.user.UserResponse;

/** What AuthService hands to the controller: the controller decides how each part is sent to the client. */
public record AuthResult(AccessToken accessToken, IssuedRefreshToken refreshToken, UserResponse user) {
}
