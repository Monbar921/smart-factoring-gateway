package ru.smart.factoring.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Проксирование /api/** в backend, access log и Swagger UI gateway.
 */
@ExtendWith(OutputCaptureExtension.class)
class SmartFactoringGatewayTest extends AbstractSmartFactoringGatewayTest {

    @Test
    void apiIsRoutedToBackendInternalRest() {
        client.get().uri("/api/deals/42?status=new")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("backend");

        assertThat(BACKEND_PATH.get()).isEqualTo("/internal/rest/deals/42?status=new");
    }

    @Test
    void maintenanceIsRoutedToBackend() {
        client.get().uri("/api/maintenance/hello")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("backend");

        assertThat(BACKEND_PATH.get()).isEqualTo("/internal/rest/maintenance/hello");
    }

    @Test
    void pathOutsideApiIsNotRouted() {
        client.get().uri("/internal/rest/deals/42")
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

    @Test
    void requestIsWrittenToAccessLog(final CapturedOutput output) {
        client.post().uri("/api/deals?draft=true")
                .header("X-Forwarded-For", "203.0.113.7")
                .exchange()
                .expectStatus().isOk();

        // access log пишется после отправки ответа, клиент может получить его раньше
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(output.getOut())
                .containsPattern("ip=127\\.0\\.0\\.1 xff=203\\.0\\.113\\.7 \"POST /api/deals\\?draft=true HTTP/1\\.1\" 200 \\d+b \\d+ms"));
    }

    @Test
    void notRoutedRequestIsWrittenToAccessLogWithItsStatus(final CapturedOutput output) {
        client.get().uri("/unknown")
                .exchange()
                .expectStatus().isNotFound();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(output.getOut())
                .contains("xff=- \"GET /unknown HTTP/1.1\" 404"));
    }

    @Test
    void healthcheckIsNotWrittenToAccessLog(final CapturedOutput output) {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
        client.get().uri("/api/deals").exchange().expectStatus().isOk();

        // запрос после healthcheck попал в лог - значит, healthcheck к этому моменту тоже был бы записан
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(output.getOut()).contains("\"GET /api/deals HTTP/1.1\""));
        assertThat(output.getOut()).doesNotContain("/actuator/health");
    }

    @Test
    void swaggerUiOffersBackendAndGatewayDefinitions() {
        client.get().uri("/v3/api-docs/swagger-config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.urls[?(@.name == 'backend')].url").isEqualTo("/backend/v3/api-docs")
                .jsonPath("$.urls[?(@.name == 'gateway')].url").isEqualTo("/v3/api-docs")
                .jsonPath("$['urls.primaryName']").isEqualTo("backend");

        client.get().uri("/swagger-ui.html")
                .exchange()
                .expectStatus().is3xxRedirection();
    }

    @Test
    void backendApiDocsAreRewrittenToGatewayPaths() {
        client.get().uri("/backend/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/deals/{id}'].get.operationId").isEqualTo("getDeal")
                .jsonPath("$.paths['/internal/rest/deals/{id}']").doesNotExist()
                .jsonPath("$.paths['/api/maintenance/hello'].get.operationId").isEqualTo("sayHello")
                .jsonPath("$.paths['/other/not-routed']").doesNotExist()
                .jsonPath("$.servers").doesNotExist()
                .jsonPath("$.info.title").isEqualTo("OpenAPI definition");

        assertThat(BACKEND_PATH.get()).isEqualTo("/v3/api-docs");
    }

    @Test
    void gatewayApiDocsAreServed() {
        client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.openapi").exists();
    }
}
