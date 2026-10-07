package cidrz.assessment.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI document is generated from the controllers on every build, so it cannot drift from the
 * code the way hand-written API documentation does. Served at {@code /v3/api-docs}, with Swagger UI
 * at {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI careLinkOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CareLink Follow-Up API")
                        .version("v1")
                        .description("""
                                Finds patients who have missed a follow-up appointment, and records \
                                contact attempts against them. All data is invented.

                                Every /api request needs a bearer token. In this slice the token is a \
                                static string mapped to a role and a scope; in production it is an \
                                OIDC access token (see the README).
                                """))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("A CareLink access token")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
