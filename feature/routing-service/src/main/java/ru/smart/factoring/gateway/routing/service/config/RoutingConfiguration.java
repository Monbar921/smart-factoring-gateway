package ru.smart.factoring.gateway.routing.service.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoutingConfiguration {
    private static final String BACKEND_ROUTE_ID = "smart-factoring-backend";

    /**
     * Внешний /api/** проксируется в /internal/rest/** backend.
     * Maintenance-эндпоинты backend наружу не открываются: на /api/maintenance/** gateway отвечает 404.
     */
    @Bean
    public RouteLocator routeLocator(final RouteLocatorBuilder builder, final RoutingProperties properties) {
        return builder.routes()
                .route(BACKEND_ROUTE_ID, route -> route
                        .path("/api/**")
                        .and().not(maintenance -> maintenance.path("/api/maintenance/**"))
                        .filters(filters -> filters.rewritePath("/api/(?<segment>.*)", "/internal/rest/${segment}"))
                        .uri(properties.backendUri()))
                .build();
    }
}
