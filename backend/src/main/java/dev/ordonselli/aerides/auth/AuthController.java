package dev.ordonselli.aerides.auth;

import java.time.Clock;
import java.time.Duration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.ordonselli.aerides.common.ratelimit.RateLimiter;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
@SecurityRequirements // these endpoints don't need a bearer token
public class AuthController {

    static final String REFRESH_COOKIE = "refresh_token";
    // The browser sends the cookie only to these endpoints, never to the rest of the API
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final SecurityProperties properties;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public AuthController(AuthService authService, SecurityProperties properties, RateLimiter rateLimiter,
            Clock clock) {
        this.authService = authService;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @PostMapping("/register")
    @Operation(summary = "Create an account and log in")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withRefreshCookie(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        // getRemoteAddr() is the real client IP thanks to server.forward-headers-strategy
        rateLimiter.checkLogin(httpRequest.getRemoteAddr(), request.email());
        return withRefreshCookie(HttpStatus.OK, authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange the refresh-token cookie for a new access token (the cookie is rotated)")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        rateLimiter.checkRefresh(httpRequest.getRemoteAddr());
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("Missing refresh token");
        }
        return withRefreshCookie(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token and clear its cookie")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthResult result) {
        Duration maxAge = Duration.between(clock.instant(), result.refreshToken().expiresAt());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken().rawValue(), maxAge).toString())
                .body(AuthResponse.bearer(result.accessToken(), result.user()));
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
