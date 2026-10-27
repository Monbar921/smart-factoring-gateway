package ru.smart.factoring.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Права по ролям realm Keycloak: operator - экран процесса, manager - эмулятор CRM.
 */
class GatewayRolesTest extends AbstractSmartFactoringGatewayTest {
    private static final String OPERATOR = "operator";
    private static final String MANAGER = "manager";

    @Test
    void operatorStartsRunsAndUploadsData() {
        assertAllowed(HttpMethod.POST, "/api/backend/runs", OPERATOR);
        assertAllowed(HttpMethod.POST, "/api/backend/manual-upload", OPERATOR);
        assertAllowed(HttpMethod.POST, "/api/backend/manual-upload/efrsb", OPERATOR);
        assertAllowed(HttpMethod.GET, "/api/backend/runs/7/upds?category=5", OPERATOR);
        assertAllowed(HttpMethod.GET, "/api/backend/parameters", OPERATOR);
    }

    @Test
    void operatorHasNoAccessToCrm() {
        assertForbidden(HttpMethod.GET, "/api/backend/managers", OPERATOR);
        assertForbidden(HttpMethod.GET, "/api/backend/clients/3/card", OPERATOR);
        assertForbidden(HttpMethod.POST, "/api/backend/offers/5/transfer", OPERATOR);
    }

    @Test
    void managerWorksWithCrmQueue() {
        assertAllowed(HttpMethod.GET, "/api/backend/managers", MANAGER);
        assertAllowed(HttpMethod.GET, "/api/backend/managers/1/queue", MANAGER);
        assertAllowed(HttpMethod.GET, "/api/backend/clients/3/card", MANAGER);
        assertAllowed(HttpMethod.POST, "/api/backend/offers/5/transfer", MANAGER);
        assertAllowed(HttpMethod.POST, "/api/backend/offers/5/reject", MANAGER);
    }

    @Test
    void managerViewsRunsButDoesNotStartThem() {
        assertAllowed(HttpMethod.GET, "/api/backend/runs", MANAGER);
        assertAllowed(HttpMethod.GET, "/api/backend/runs/7/funnel", MANAGER);
        assertForbidden(HttpMethod.POST, "/api/backend/runs", MANAGER);
        assertForbidden(HttpMethod.POST, "/api/backend/manual-upload", MANAGER);
    }

    @Test
    void userWithBothRolesHasFullAccess() {
        assertAllowed(HttpMethod.POST, "/api/backend/runs", OPERATOR, MANAGER);
        assertAllowed(HttpMethod.POST, "/api/backend/offers/5/transfer", OPERATOR, MANAGER);
    }

    @Test
    void userWithoutRolesHasNoAccessToProcessAndCrm() {
        assertForbidden(HttpMethod.GET, "/api/backend/runs");
        assertForbidden(HttpMethod.GET, "/api/backend/managers");
    }

    @Test
    void otherBackendPathsNeedOnlyLogin() {
        assertAllowed(HttpMethod.GET, "/api/backend/maintenance/whoami");
    }

    private void assertAllowed(final HttpMethod method, final String uri, final String... roles) {
        BACKEND_PATH.set(null);
        client.method(method).uri(uri)
                .headers(headers -> headers.setBearerAuth(tokenWithRoles(roles)))
                .exchange()
                .expectStatus().isOk();

        assertThat(BACKEND_PATH.get()).isEqualTo(uri.replace("/api/backend/", "/internal/rest/"));
    }

    private void assertForbidden(final HttpMethod method, final String uri, final String... roles) {
        BACKEND_PATH.set(null);
        client.method(method).uri(uri)
                .headers(headers -> headers.setBearerAuth(tokenWithRoles(roles)))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(BACKEND_PATH.get()).isNull();
    }
}
