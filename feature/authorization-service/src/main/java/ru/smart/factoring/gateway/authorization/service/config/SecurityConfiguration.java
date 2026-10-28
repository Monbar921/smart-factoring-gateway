package ru.smart.factoring.gateway.authorization.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import ru.smart.factoring.gateway.authorization.service.guest.GuestSessionController;

/**
 * Gateway - единственная точка проверки пользователя: каждый запрос несёт access token Keycloak
 * в заголовке Authorization: Bearer, gateway проверяет подпись (ключи из JWKS Keycloak), issuer и срок действия.
 * <p>
 * Права - по ролям realm Keycloak (claim realm_access.roles):
 * <ul>
 *     <li>{@value #OPERATOR} - экран процесса: импорт данных ручной загрузки и запуск прогона;</li>
 *     <li>{@value #MANAGER} - эмулятор CRM: очередь, карточка клиента, решения по предложениям.</li>
 * </ul>
 * Смотреть прогоны и параметры могут обе роли. Остальные пути backend - любому вошедшему пользователю.
 * <p>
 * Заголовок Authorization проксируется в backend как есть: backend токен не проверяет,
 * только читает из него payload - он доступен только из сети стенда, то есть только через gateway.
 * <p>
 * Без сессий и CSRF: состояние - только в токене.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {
    public static final String OPERATOR = "operator";
    public static final String MANAGER = "manager";

    /**
     * Без токена: healthcheck docker, Swagger UI и спецификации (кнопка Authorize получает токен в самом Swagger UI),
     * гостевой вход «Запуск демо» - он и выдаёт токен.
     */
    private static final String[] PUBLIC_PATHS = {
        "/actuator/health", "/actuator/health/**", GuestSessionController.PATH,
        "/swagger-ui.html", "/swagger-ui/**", "/webjars/**",
        "/v3/api-docs", "/v3/api-docs/**", "/backend/v3/api-docs"
    };

    private static final String BACKEND = "/api/backend";
    private static final String[] RUN_VIEW_PATHS = {BACKEND + "/runs", BACKEND + "/runs/**", BACKEND + "/parameters"};
    private static final String[] OPERATOR_PATHS = {BACKEND + "/runs", BACKEND + "/runs/**", BACKEND + "/manual-upload/**"};
    private static final String[] MANAGER_PATHS = {BACKEND + "/managers/**", BACKEND + "/clients/**", BACKEND + "/offers/**"};

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(final ServerHttpSecurity http) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .pathMatchers(HttpMethod.GET, RUN_VIEW_PATHS).hasAnyRole(OPERATOR, MANAGER)
                        .pathMatchers(OPERATOR_PATHS).hasRole(OPERATOR)
                        .pathMatchers(MANAGER_PATHS).hasRole(MANAGER)
                        .anyExchange().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .build();
    }

    private static ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        final var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRolesConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
