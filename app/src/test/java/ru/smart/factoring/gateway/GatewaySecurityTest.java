package ru.smart.factoring.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gateway пускает к backend только с действующим токеном Keycloak и передаёт этот токен backend как есть.
 */
class GatewaySecurityTest extends AbstractSmartFactoringGatewayTest {

    @Test
    void validTokenIsPassedToBackendAsIs() {
        final String token = token();

        client.get().uri("/api/backend/deals/42")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk();

        assertThat(BACKEND_AUTHORIZATION.get()).isEqualTo("Bearer " + token);
    }

    @Test
    void requestWithoutTokenIsRejected() {
        client.get().uri("/api/backend/deals/42")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches(HttpHeaders.WWW_AUTHENTICATE, "Bearer.*");

        assertThat(BACKEND_PATH.get()).isNull();
    }

    @Test
    void expiredTokenIsRejected() {
        assertRejected(expiredToken());
    }

    @Test
    void tokenFromOtherRealmIsRejected() {
        assertRejected(tokenFromOtherIssuer());
    }

    @Test
    void tokenSignedByUnknownKeyIsRejected() {
        assertRejected(tokenSignedByForeignKey());
    }

    @Test
    void garbageTokenIsRejected() {
        assertRejected("not-a-jwt");
    }

    @Test
    void actuatorBeyondHealthRequiresToken() {
        client.get().uri("/actuator/metrics").exchange().expectStatus().isUnauthorized();
        client.get().uri("/actuator/metrics").headers(headers -> headers.setBearerAuth(token())).exchange().expectStatus().isOk();
    }

    @Test
    void swaggerIsPublic() {
        client.get().uri("/swagger-ui/index.html").exchange().expectStatus().isOk();
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk();
        client.get().uri("/backend/v3/api-docs").exchange().expectStatus().isOk();
    }

    private void assertRejected(final String token) {
        client.get().uri("/api/backend/deals/42")
                .headers(headers -> headers.setBearerAuth(token))
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(BACKEND_PATH.get()).isNull();
    }
}
