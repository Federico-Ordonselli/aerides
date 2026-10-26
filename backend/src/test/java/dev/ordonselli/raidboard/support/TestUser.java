package dev.ordonselli.raidboard.support;

/** A user registered through the API during a test, with a valid access token. */
public record TestUser(Long id, String email, String accessToken) {
}
