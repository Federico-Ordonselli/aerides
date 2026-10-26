package dev.ordonselli.raidboard.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI metadata. The "bearerAuth" scheme adds the "Authorize" button,
 * where you can paste an access token obtained from /api/auth/login.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Raidboard API", version = "v1",
                description = "Organize gaming group sessions: events, role slots, sign-ups and waitlists"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
