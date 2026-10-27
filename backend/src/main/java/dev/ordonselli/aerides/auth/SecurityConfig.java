package dev.ordonselli.aerides.auth;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless JWT security.
 * <p>
 * Access tokens are validated by Spring's built-in OAuth2 resource server support, so there is no
 * hand-written JWT filter. Tokens are signed with HMAC-SHA256 using a secret from the environment.
 * <p>
 * CSRF protection is disabled because the API authenticates with the Authorization header, which
 * browsers never attach automatically. The only cookie (the refresh token) is SameSite=Strict and
 * limited to /api/auth, so a cross-site request cannot use it.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    static final int MIN_JWT_SECRET_BYTES = 32;

    private static final String[] PUBLIC_PATHS = {
            "/api/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health/**",
            // WebSocket handshake: browsers can't send the Authorization header there, so the token is
            // checked on the STOMP CONNECT frame instead (see realtime.StompAuthInterceptor)
            "/ws",
            "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        // Delegate 401s to GlobalExceptionHandler so they are ProblemDetail JSON like every other error
                        .authenticationEntryPoint((request, response, ex) ->
                                exceptionResolver.resolveException(request, response, null, ex)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) ->
                                exceptionResolver.resolveException(request, response, null, ex))
                        .accessDeniedHandler((request, response, ex) ->
                                exceptionResolver.resolveException(request, response, null, ex)));
        return http.build();
    }

    @Bean
    public SecretKey jwtSigningKey(SecurityProperties properties) {
        return signingKey(properties.jwtSecret());
    }

    /**
     * Checked here and not with bean validation on {@link SecurityProperties}: a binding failure prints the
     * rejected value in the startup log, and this value is the secret.
     */
    static SecretKey signingKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new InvalidJwtSecretException("JWT_SECRET is not set. There is no default outside local development.");
        }
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        // HS256 needs a key of at least 256 bits; bytes count, not characters
        if (key.length < MIN_JWT_SECRET_BYTES) {
            throw new InvalidJwtSecretException(
                    "JWT_SECRET is too short: HS256 needs at least " + MIN_JWT_SECRET_BYTES + " bytes.");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSigningKey).build();
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Default validators (expiry, not-before) plus a check that we issued the token
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** The readiness probe, polled cross-origin by the static page that wakes the demo up. */
    static final String READINESS_PATH = "/actuator/health/readiness";

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Registered before "/**" because the first matching pattern wins. Only GET, no cookies, no extra headers:
        // a "simple" request, so the browser doesn't even send a preflight
        if (!properties.statusPageOrigins().isEmpty()) {
            CorsConfiguration readiness = new CorsConfiguration();
            readiness.setAllowedOrigins(properties.statusPageOrigins());
            readiness.addAllowedMethod(HttpMethod.GET);
            readiness.setAllowCredentials(false);
            source.registerCorsConfiguration(READINESS_PATH, readiness);
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        // Needed so the browser sends the refresh-token cookie on cross-origin calls in development
        config.setAllowCredentials(true);
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
