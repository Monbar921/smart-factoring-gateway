package ru.smart.factoring.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Вместо backend поднимается заглушка на Reactor Netty: отвечает 200 и запоминает путь пришедшего запроса.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SmartFactoringGatewayTest {
    private static final AtomicReference<String> BACKEND_PATH = new AtomicReference<>();

    private static final DisposableServer BACKEND = HttpServer.create()
            .port(0)
            .handle((request, response) -> {
                BACKEND_PATH.set(request.uri());
                return response.sendString(Mono.just("backend"));
            })
            .bindNow();

    @DynamicPropertySource
    static void backendUri(final DynamicPropertyRegistry registry) {
        registry.add("smart-factoring.gateway.backend-uri", () -> "http://localhost:" + BACKEND.port());
    }

    @AfterAll
    static void stopBackend() {
        BACKEND.disposeNow();
    }

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        BACKEND_PATH.set(null);
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void apiIsRoutedToBackendInternalRest() {
        client.get().uri("/api/deals/42?status=new")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("backend");

        assertThat(BACKEND_PATH.get()).isEqualTo("/internal/rest/deals/42?status=new");
    }

    @Test
    void maintenanceIsNotExposed() {
        client.get().uri("/api/maintenance/hello")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(BACKEND_PATH.get()).isNull();
    }

    @Test
    void healthIsUp() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
