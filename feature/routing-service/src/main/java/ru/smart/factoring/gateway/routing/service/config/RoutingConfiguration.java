package ru.smart.factoring.gateway.routing.service.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import ru.smart.factoring.gateway.routing.service.swagger.BackendApiDocsTransformer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RoutingConfiguration {
    private static final String BACKEND_ROUTE_ID = "smart-factoring-backend";
    private static final String BACKEND_API_DOCS_ROUTE_ID = "smart-factoring-backend-api-docs";

    /**
     * Внешний /api/** проксируется в /internal/rest/** backend.
     * <p>
     * /backend/v3/api-docs - спецификация backend для Swagger UI gateway, переписанная под эти же пути.
     */
    @Bean
    public RouteLocator routeLocator(final RouteLocatorBuilder builder,
                                     final RoutingProperties properties,
                                     final JsonMapper jsonMapper) {
        final var apiDocsTransformer = new BackendApiDocsTransformer(jsonMapper);
        return builder.routes()
                .route(BACKEND_ROUTE_ID, route -> route
                        .path("/api/backend/**")
                        .filters(filters -> filters.rewritePath("/api/backend/(?<segment>.*)", "/internal/rest/${segment}"))
                        .uri(properties.backendUri()))
                .route(BACKEND_API_DOCS_ROUTE_ID, route -> route
                        .path("/backend/v3/api-docs")
                        .filters(filters -> filters
                                .setPath("/v3/api-docs")
                                .modifyResponseBody(String.class, String.class,
                                        (exchange, apiDocs) -> Mono.justOrEmpty(apiDocs).map(apiDocsTransformer::transform)))
                        .uri(properties.backendUri()))
                .build();
    }
}
