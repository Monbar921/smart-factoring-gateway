package ru.smart.factoring.gateway;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Вместо backend и Keycloak поднимается заглушка на Reactor Netty:
 * <ul>
 *     <li>/jwks - публичный ключ, которым подписываются тестовые токены (как JWKS Keycloak);</li>
 *     <li>/v3/api-docs - спецификация OpenAPI в том виде, в каком её отдаёт springdoc backend;</li>
 *     <li>остальное - "backend", путь и заголовок Authorization запроса запоминаются.</li>
 * </ul>
 * Заглушка одна на все тестовые классы и останавливается вместе с JVM.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractSmartFactoringGatewayTest {
    static final String ISSUER = "http://keycloak.test/realms/smart-factoring-test";

    static final String BACKEND_API_DOCS = """
            {
              "openapi": "3.1.0",
              "info": {"title": "OpenAPI definition", "version": "v0"},
              "servers": [{"url": "http://backend:8080", "description": "Generated server url"}],
              "paths": {
                "/internal/rest/deals/{id}": {"get": {"operationId": "getDeal", "responses": {"200": {"description": "OK"}}}},
                "/internal/rest/maintenance/hello": {"get": {"operationId": "sayHello", "responses": {"200": {"description": "OK"}}}},
                "/other/not-routed": {"get": {"operationId": "notRouted", "responses": {"200": {"description": "OK"}}}}
              }
            }
            """;

    static final AtomicReference<String> BACKEND_PATH = new AtomicReference<>();
    static final AtomicReference<String> BACKEND_AUTHORIZATION = new AtomicReference<>();
    static final AtomicReference<String> GUEST_TOKEN_FORM = new AtomicReference<>();

    private static final RSAKey SIGNING_KEY = generateKey("smart-factoring-test");
    /** Чужой ключ: токен им подписан, но в JWKS его нет. */
    private static final RSAKey FOREIGN_KEY = generateKey("foreign");

    private static final DisposableServer BACKEND = HttpServer.create()
            .port(0)
            .handle((request, response) -> {
                if (request.uri().equals("/token")) {
                    // token endpoint Keycloak для гостевого входа: запоминаем форму, отвечаем токеном демо-пользователя
                    return request.receive().aggregate().asString().flatMap(form -> {
                        GUEST_TOKEN_FORM.set(form);
                        return Mono.just(form);
                    }).then(response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .sendString(Mono.fromSupplier(() -> "{\"access_token\":\"" + tokenWithRoles("operator", "manager")
                                    + "\",\"expires_in\":900,\"refresh_token\":\"secret-refresh\"}")).then());
                }
                if (request.uri().equals("/jwks")) {
                    return response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .sendString(Mono.just(new JWKSet(SIGNING_KEY.toPublicJWK()).toString()));
                }
                BACKEND_PATH.set(request.uri());
                BACKEND_AUTHORIZATION.set(request.requestHeaders().get(HttpHeaders.AUTHORIZATION));
                if (request.uri().equals("/v3/api-docs")) {
                    return response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .sendString(Mono.just(BACKEND_API_DOCS));
                }
                return response.sendString(Mono.just("backend"));
            })
            .bindNow();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(BACKEND::disposeNow));
    }

    @DynamicPropertySource
    static void backendAndKeycloak(final DynamicPropertyRegistry registry) {
        registry.add("BACKEND_URI", () -> "http://localhost:" + BACKEND.port());
        registry.add("KEYCLOAK_ISSUER_URI", () -> ISSUER);
        registry.add("KEYCLOAK_JWK_SET_URI", () -> "http://localhost:" + BACKEND.port() + "/jwks");
        registry.add("GUEST_ENABLED", () -> "true");
        registry.add("KEYCLOAK_TOKEN_URI", () -> "http://localhost:" + BACKEND.port() + "/token");
        registry.add("GUEST_CLIENT_SECRET", () -> "guest-secret");
        registry.add("GUEST_PASSWORD", () -> "demo-password");
    }

    @LocalServerPort
    private int port;

    protected WebTestClient client;

    @BeforeEach
    void setUpClient() {
        BACKEND_PATH.set(null);
        BACKEND_AUTHORIZATION.set(null);
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /**
     * Access token как от Keycloak realm smart-factoring-test.
     */
    static String token() {
        return sign(SIGNING_KEY, claims(ISSUER, Instant.now().plusSeconds(300)));
    }

    /**
     * Access token с заданными ролями realm (realm_access.roles).
     */
    static String tokenWithRoles(final String... roles) {
        return sign(SIGNING_KEY, claims(ISSUER, Instant.now().plusSeconds(300), List.of(roles)));
    }

    static String expiredToken() {
        return sign(SIGNING_KEY, claims(ISSUER, Instant.now().minusSeconds(300)));
    }

    static String tokenFromOtherIssuer() {
        return sign(SIGNING_KEY, claims("http://keycloak.test/realms/other", Instant.now().plusSeconds(300)));
    }

    static String tokenSignedByForeignKey() {
        return sign(FOREIGN_KEY, claims(ISSUER, Instant.now().plusSeconds(300)));
    }

    private static JWTClaimsSet claims(final String issuer, final Instant expiresAt) {
        return claims(issuer, expiresAt, List.of("user"));
    }

    private static JWTClaimsSet claims(final String issuer, final Instant expiresAt, final List<String> roles) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(UUID.randomUUID().toString())
                .issueTime(Date.from(expiresAt.minusSeconds(600)))
                .expirationTime(Date.from(expiresAt))
                .claim("preferred_username", "tester")
                .claim("realm_access", Map.of("roles", roles))
                .build();
    }

    private static String sign(final RSAKey key, final JWTClaimsSet claims) {
        try {
            final SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (final JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey generateKey(final String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (final JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
