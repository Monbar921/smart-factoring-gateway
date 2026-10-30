package ru.smart.factoring.gateway.authorization.service.guest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

/**
 * Гостевой вход «Запуск демо» (П1-2): токен демо-пользователя по Resource Owner Password Grant конфиденциального
 * клиента Keycloak. Гость только смотрит (замечания 30.10, П4-1, П4-2): токен выдан клиентом гостя (claim azp), и gateway
 * даёт ему только роль viewer, какие бы роли ни были у демо-пользователя (KeycloakRealmRolesConverter).
 * <p>
 * Наружу - только access token и срок: refresh token и учётные данные остаются в gateway. Истёк токен - UI просит новый.
 */
@RestController
@EnableConfigurationProperties(GuestProperties.class)
public class GuestSessionController {
    public static final String PATH = "/api/guest/session";

    private final GuestProperties properties;
    private final WebClient webClient;

    public GuestSessionController(final GuestProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.create();
    }

    @PostMapping(value = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GuestSession> session() {
        if (!properties.enabled()) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Гостевой вход выключен"));
        }
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("username", properties.username());
        form.add("password", properties.password());
        form.add("scope", "openid profile");
        return webClient.post()
                .uri(properties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(token -> new GuestSession(token.path("access_token").asString(), token.path("expires_in").asLong()))
                .onErrorMap(error -> !(error instanceof ResponseStatusException),
                        error -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak не выдал гостевой токен", error));
    }

    /**
     * @param accessToken access token демо-пользователя
     * @param expiresIn   сколько секунд он действует
     */
    public record GuestSession(String accessToken, long expiresIn) {
    }
}
