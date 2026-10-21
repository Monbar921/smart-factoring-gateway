package ru.smart.factoring.gateway;

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

import java.util.concurrent.atomic.AtomicReference;

/**
 * Вместо backend поднимается заглушка на Reactor Netty: на /v3/api-docs отдаёт спецификацию OpenAPI
 * в том виде, в каком её отдаёт springdoc backend, на остальное - "backend". Путь пришедшего запроса запоминается.
 * <p>
 * Заглушка одна на все тестовые классы и останавливается вместе с JVM.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractSmartFactoringGatewayTest {
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

    private static final DisposableServer BACKEND = HttpServer.create()
            .port(0)
            .handle((request, response) -> {
                BACKEND_PATH.set(request.uri());
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
    static void backendUri(final DynamicPropertyRegistry registry) {
        registry.add("smart-factoring.gateway.backend-uri", () -> "http://localhost:" + BACKEND.port());
    }

    @LocalServerPort
    private int port;

    protected WebTestClient client;

    @BeforeEach
    void setUpClient() {
        BACKEND_PATH.set(null);
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
