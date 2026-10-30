package ru.smart.factoring.gateway.authorization.service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;
import ru.smart.factoring.gateway.authorization.service.guest.GuestProperties;
import ru.smart.factoring.gateway.authorization.service.guest.GuestSessionController;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.stream.Collectors;

/**
 * Gateway - единственная точка проверки пользователя: каждый запрос несёт access token Keycloak
 * в заголовке Authorization: Bearer, gateway проверяет подпись (ключи из JWKS Keycloak), issuer и срок действия.
 * <p>
 * Права - по ролям realm Keycloak (claim realm_access.roles):
 * <ul>
 *     <li>{@value #OPERATOR} - экран процесса: импорт данных ручной загрузки и запуск прогона;</li>
 *     <li>{@value #MANAGER} - эмулятор CRM: очередь, карточка клиента, решения по предложениям;</li>
 *     <li>{@value #VIEWER} - гость «Запуск демо» (П4-1, П4-2): только просмотр - экран процесса, CRM, история;
 *     любой запрос, кроме GET, - 403, что бы ни показывал UI.</li>
 * </ul>
 * Смотреть прогоны и параметры могут все три роли. Остальные пути backend: GET - любому вошедшему пользователю,
 * изменение - любому, кроме гостя.
 * <p>
 * Отказ в доступе пишется в лог gateway: кто, с какими ролями, что пытался сделать (П4-6).
 * <p>
 * Заголовок Authorization проксируется в backend как есть: backend токен не проверяет,
 * только читает из него payload - он доступен только из сети стенда, то есть только через gateway.
 * <p>
 * Без сессий и CSRF: состояние - только в токене.
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {
    public static final String OPERATOR = "operator";
    public static final String MANAGER = "manager";
    public static final String VIEWER = "viewer";

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
    private static final String VIEWER_AUTHORITY = "ROLE_" + VIEWER;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(final ServerHttpSecurity http, final GuestProperties guest) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .pathMatchers(HttpMethod.GET, RUN_VIEW_PATHS).hasAnyRole(OPERATOR, MANAGER, VIEWER)
                        .pathMatchers(HttpMethod.GET, MANAGER_PATHS).hasAnyRole(MANAGER, VIEWER)
                        .pathMatchers(OPERATOR_PATHS).hasRole(OPERATOR)
                        .pathMatchers(MANAGER_PATHS).hasRole(MANAGER)
                        .pathMatchers(HttpMethod.GET).authenticated()
                        // гость не меняет данные и не тратит запросы LLM ни через какой путь
                        .anyExchange().access((authentication, context) -> authentication
                                .map(auth -> (AuthorizationResult) new AuthorizationDecision(auth.isAuthenticated() && !isViewer(auth)))
                                .defaultIfEmpty(new AuthorizationDecision(false))))
                .exceptionHandling(handling -> handling.accessDeniedHandler(accessDeniedHandler()))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter(guest.clientId()))))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .build();
    }

    /**
     * 403 в формате ошибок backend ({code, message, details}) и запись в лог: кто и что пытался сделать.
     */
    private static ServerAccessDeniedHandler accessDeniedHandler() {
        return (exchange, denied) -> exchange.getPrincipal()
                .map(SecurityConfiguration::describe)
                .defaultIfEmpty("аноним")
                .flatMap(who -> {
                    final var request = exchange.getRequest();
                    log.warn("Отказ в доступе (нет прав): {} - {} {}", who, request.getMethod(), request.getPath().value());
                    final var response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.FORBIDDEN);
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    final String message = who.contains(VIEWER_AUTHORITY)
                            ? "Гостю доступен только просмотр" : "Нет прав на это действие";
                    final DataBuffer body = response.bufferFactory().wrap(("{\"code\":\"FORBIDDEN\",\"message\":\"" + message
                            + "\",\"details\":{}}").getBytes(StandardCharsets.UTF_8));
                    return response.writeWith(Mono.just(body));
                });
    }

    private static String describe(final Principal principal) {
        final String roles = principal instanceof Authentication auth
                ? auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","))
                : "";
        final String name = principal instanceof JwtAuthenticationToken jwt && jwt.getToken().getClaimAsString("preferred_username") != null
                ? jwt.getToken().getClaimAsString("preferred_username") : principal.getName();
        return name + " [" + roles + "]";
    }

    private static boolean isViewer(final Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority -> VIEWER_AUTHORITY.equals(authority.getAuthority()));
    }

    private static ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter(final String guestClientId) {
        final var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRolesConverter(guestClientId));
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
