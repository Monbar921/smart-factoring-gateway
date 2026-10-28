package ru.smart.factoring.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Гостевой вход «Запуск демо» (замечания 28.10, П1-2): без токена, учётные данные демо-пользователя - только в gateway,
 * наружу - access token с правами оператора и менеджера.
 */
class GatewayGuestTest extends AbstractSmartFactoringGatewayTest {

    @Test
    void issuesDemoUserTokenWithoutPassword() {
        final JsonNode session = client.post().uri("/api/guest/session")
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(session).isNotNull();
        assertThat(session.get("expiresIn").asLong()).isEqualTo(900);
        // refresh token и учётные данные наружу не уходят
        assertThat(session.toString()).doesNotContain("secret-refresh", "demo-password", "guest-secret");
        assertThat(GUEST_TOKEN_FORM.get())
                .contains("grant_type=password", "client_id=smart-factoring-guest", "client_secret=guest-secret", "username=demo");

        // токен гостя - обычный токен с ролями: процесс и CRM доступны
        final String token = session.get("accessToken").asString();
        for (final String uri : new String[]{"/api/backend/manual-upload", "/api/backend/offers/1/transfer"}) {
            client.method(HttpMethod.POST).uri(uri)
                    .headers(headers -> headers.setBearerAuth(token))
                    .exchange()
                    .expectStatus().isOk();
            assertThat(BACKEND_PATH.get()).isEqualTo(uri.replace("/api/backend/", "/internal/rest/"));
        }
    }
}
