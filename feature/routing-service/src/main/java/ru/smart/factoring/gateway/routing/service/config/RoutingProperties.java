package ru.smart.factoring.gateway.routing.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * Адреса downstream-сервисов, куда gateway проксирует запросы.
 *
 * @param backendUri адрес smart-factoring-backend, например http://backend:8080
 * @param swagger    куда Swagger UI ходит за токеном по кнопке Authorize
 */
@ConfigurationProperties(prefix = "smart-factoring.gateway")
public record RoutingProperties(URI backendUri, Swagger swagger) {

    /**
     * Эндпоинты Keycloak для OAuth2 Authorization Code + PKCE в Swagger UI: публичные адреса, их открывает браузер.
     *
     * @param authorizationUrl страница логина Keycloak
     * @param tokenUrl         обмен кода на токен
     */
    public record Swagger(String authorizationUrl, String tokenUrl) {
    }
}
