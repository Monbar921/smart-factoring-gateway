package ru.smart.factoring.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.smart.factoring.gateway.routing.service.config.RoutingProperties;
import ru.smart.factoring.gateway.routing.service.swagger.BackendApiDocsTransformer;

/**
 * Спецификация самого gateway (эндпоинты actuator) с той же схемой Keycloak, что и у backend:
 * токен, полученный кнопкой Authorize, работает в обеих спецификациях.
 */
@Configuration
public class SwaggerConfiguration {

    @Bean
    public OpenAPI gatewayOpenApi(final RoutingProperties properties) {
        final OpenAPI openApi = new OpenAPI().info(new Info().title("smart-factoring-gateway"));
        final RoutingProperties.Swagger swagger = properties.swagger();
        if (swagger == null) {
            return openApi;
        }
        final OAuthFlow authorizationCode = new OAuthFlow()
                .authorizationUrl(swagger.authorizationUrl())
                .tokenUrl(swagger.tokenUrl())
                .scopes(new Scopes().addString("openid", "OpenID Connect"));
        return openApi
                .components(new Components().addSecuritySchemes(BackendApiDocsTransformer.SECURITY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .flows(new OAuthFlows().authorizationCode(authorizationCode))))
                .addSecurityItem(new SecurityRequirement().addList(BackendApiDocsTransformer.SECURITY_SCHEME, "openid"));
    }
}
