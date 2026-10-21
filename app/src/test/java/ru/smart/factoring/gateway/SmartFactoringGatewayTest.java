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
 * Проксирование /api/backend/** в backend, access log и Swagger UI gateway.
 */
@ExtendWith(OutputCaptureExtension.class)
class SmartFactoringGatewayTest extends AbstractSmartFactoringGatewayTest {

    @Test
    void apiIsRoutedToBackendInternalRest() {
        client.get().uri("/api/backend/deals/42?status=new")
                .headers(headers -> headers.setBearerAuth(token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("backend");

        assertThat(BACKEND_PATH.get()).isEqualTo("/internal/rest/deals/42?status=new");
    }

    @Test
    void maintenanceIsRoutedToBackend() {
        client.get().uri("/api/backend/maintenance/hello")
                .headers(headers -> headers.setBearerAuth(token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("backend");

        assertThat(BACKEND_PATH.get()).isEqualTo("/internal/rest/maintenance/hello");
    }

    @Test
    void pathOutsideApiIsNotRouted() {
        client.get().uri("/internal/rest/deals/42")
                .headers(headers -> headers.setBearerAuth(token()))
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
        client.post().uri("/api/backend/deals?draft=true")
                .headers(headers -> headers.setBearerAuth(token()))
                .header("X-Forwarded-For", "203.0.113.7")
                .exchange()
                .expectStatus().isOk();

        // access log пишется после отправки ответа, клиент может получить его раньше
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(output.getOut())
                .containsPattern("ip=127\\.0\\.0\\.1 xff=203\\.0\\.113\\.7 \"POST /api/backend/deals\\?draft=true HTTP/1\\.1\" 200 \\d+b \\d+ms"));
    }

    @Test
    void rejectedRequestIsWrittenToAccessLogWithItsStatus(final CapturedOutput output) {
        client.get().uri("/api/backend/deals")
                .exchange()
                .expectStatus().isUnauthorized();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(output.getOut())
                .contains("xff=- \"GET /api/backend/deals HTTP/1.1\" 401"));
    }

    @Test
    void healthcheckIsNotWrittenToAccessLog(final CapturedOutput output) {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
        client.get().uri("/api/backend/deals").headers(headers -> headers.setBearerAuth(token())).exchange().expectStatus().isOk();

        // запрос после healthcheck попал в лог - значит, healthcheck к этому моменту тоже был бы записан
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(output.getOut()).contains("\"GET /api/backend/deals HTTP/1.1\""));
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
                .jsonPath("$.paths['/api/backend/deals/{id}'].get.operationId").isEqualTo("getDeal")
                .jsonPath("$.paths['/api/backend/maintenance/hello'].get.operationId").isEqualTo("sayHello")
                .jsonPath("$.paths['/internal/rest/deals/{id}']").doesNotExist()
                .jsonPath("$.paths['/other/not-routed']").doesNotExist()
                .jsonPath("$.servers").doesNotExist()
                .jsonPath("$.info.title").isEqualTo("OpenAPI definition");

        assertThat(BACKEND_PATH.get()).isEqualTo("/v3/api-docs");
    }

    @Test
    void backendApiDocsRequireKeycloakToken() {
        client.get().uri("/backend/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.securitySchemes.keycloak.type").isEqualTo("oauth2")
                .jsonPath("$.components.securitySchemes.keycloak.flows.authorizationCode.authorizationUrl")
                .isEqualTo(ISSUER + "/protocol/openid-connect/auth")
                .jsonPath("$.components.securitySchemes.keycloak.flows.authorizationCode.tokenUrl")
                .isEqualTo(ISSUER + "/protocol/openid-connect/token")
                .jsonPath("$.security[0].keycloak[0]").isEqualTo("openid");
    }

    @Test
    void gatewayApiDocsAreServedWithKeycloakScheme() {
        client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.openapi").exists()
                .jsonPath("$.components.securitySchemes.keycloak.type").isEqualTo("oauth2");
    }

    @Test
    void swaggerUiUsesPkceClient() {
        client.get().uri("/v3/api-docs/swagger-config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.oauth2RedirectUrl").exists();
    }
}
