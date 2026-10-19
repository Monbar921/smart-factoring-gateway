package ru.smart.factoring.gateway.routing.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * Адреса downstream-сервисов, куда gateway проксирует запросы.
 *
 * @param backendUri адрес smart-factoring-backend, например http://backend:8080
 */
@ConfigurationProperties(prefix = "smart-factoring.gateway")
public record RoutingProperties(URI backendUri) {
}
