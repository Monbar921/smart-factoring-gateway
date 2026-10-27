package ru.smart.factoring.gateway.routing.service.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.RouteMetadataUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import ru.smart.factoring.gateway.routing.service.swagger.BackendApiDocsTransformer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RoutingConfiguration {
    private static final String BACKEND_ROUTE_ID = "smart-factoring-backend";
    private static final String BACKEND_API_DOCS_ROUTE_ID = "smart-factoring-backend-api-docs";
    private static final String BACKEND_DEMO_ROUTE_ID = "smart-factoring-backend-demo";
    private static final long DEMO_RESPONSE_TIMEOUT_MS = 600_000;

    /**
     * Внешний /api/backend/** проксируется в /internal/rest/** backend.
     * <p>
     * /backend/v3/api-docs - спецификация backend для Swagger UI gateway, переписанная под эти же пути.
     */
    @Bean
    public RouteLocator routeLocator(final RouteLocatorBuilder builder,
                                     final RoutingProperties properties,
                                     final JsonMapper jsonMapper) {
        final var apiDocsTransformer = new BackendApiDocsTransformer(jsonMapper, properties.swagger());
        return builder.routes()
                // импорт демо-данных дольше общего response-timeout: набор партий ML-команды - около минуты и больше
                .route(BACKEND_DEMO_ROUTE_ID, route -> route
                        .order(-1)
                        .path("/api/backend/demo/**")
                        .filters(filters -> filters.rewritePath("/api/backend/(?<segment>.*)", "/internal/rest/${segment}"))
                        .metadata(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, DEMO_RESPONSE_TIMEOUT_MS)
                        .uri(properties.backendUri()))
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
