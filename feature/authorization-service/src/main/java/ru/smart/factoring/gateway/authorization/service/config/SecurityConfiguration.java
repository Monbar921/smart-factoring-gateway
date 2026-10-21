package ru.smart.factoring.gateway.authorization.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Gateway - единственная точка проверки пользователя: каждый запрос несёт access token Keycloak
 * в заголовке Authorization: Bearer, gateway проверяет подпись (ключи из JWKS Keycloak), issuer и срок действия.
 * <p>
 * Заголовок Authorization проксируется в backend как есть: backend токен не проверяет,
 * только читает из него payload - он доступен только из сети стенда, то есть только через gateway.
 * <p>
 * Без сессий и CSRF: состояние - только в токене.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {
    /**
     * Без токена: healthcheck docker, Swagger UI и спецификации (кнопка Authorize получает токен в самом Swagger UI).
     */
    private static final String[] PUBLIC_PATHS = {
        "/actuator/health", "/actuator/health/**",
        "/swagger-ui.html", "/swagger-ui/**", "/webjars/**",
        "/v3/api-docs", "/v3/api-docs/**", "/backend/v3/api-docs"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(final ServerHttpSecurity http) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .build();
    }
}
