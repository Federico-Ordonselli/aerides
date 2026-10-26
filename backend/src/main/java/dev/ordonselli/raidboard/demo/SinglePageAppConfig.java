package dev.ordonselli.raidboard.demo;

import java.io.IOException;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Demo deployment only: the Angular build is copied into {@code classpath:/static} (see
 * {@code deploy/render/Dockerfile}) and served by Spring Boot, so app, API and WebSocket share one origin
 * and one free service. Everywhere else nginx serves the app and proxies the API.
 */
@Configuration
@Profile("demo")
public class SinglePageAppConfig implements WebMvcConfigurer {

    /** Paths that belong to the backend: they keep the normal, default-deny security chain. */
    private static final List<String> BACKEND_PREFIXES =
            List.of("/api/", "/ws", "/actuator", "/v3/api-docs", "/swagger-ui");

    /**
     * Static files and Angular routes hold no data, so they are public. This chain runs before the main
     * one ({@code @Order(1)}) and only for GET requests outside the backend paths.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain frontendSecurityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher frontendRequests = request -> HttpMethod.GET.matches(request.getMethod())
                && BACKEND_PREFIXES.stream().noneMatch(request.getRequestURI()::startsWith);
        http
                .securityMatcher(frontendRequests)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Existing files are served as they are. A path without a file extension is an Angular route
     * (e.g. {@code /groups/1}), so it gets {@code index.html} and the Angular router takes over.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SinglePageAppResourceResolver());
    }

    static class SinglePageAppResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            if (!resourcePath.isEmpty()) {
                Resource requested = location.createRelative(resourcePath);
                if (requested.exists() && requested.isReadable()) {
                    return requested;
                }
            }
            // A missing file or an unknown API path is a 404, not the app
            if (resourcePath.contains(".") || resourcePath.startsWith("api/")) {
                return null;
            }
            return location.createRelative("index.html");
        }
    }
}
