package ru.smart.factoring.gateway.routing.service.swagger;

import ru.smart.factoring.gateway.routing.service.config.RoutingProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Переписывает OpenAPI-спецификацию backend под то, как его API видно снаружи через gateway:
 * <ul>
 *     <li>/internal/rest/** -> /api/backend/** - те же пути, что проксирует RoutingConfiguration;</li>
 *     <li>пути вне /internal/rest убираются: через gateway они недоступны;</li>
 *     <li>servers убирается: там внутренний адрес backend (http://backend:8080), Swagger UI без servers
 *     шлёт запросы на тот же адрес, с которого открыт, то есть через gateway;</li>
 *     <li>добавляется схема безопасности Keycloak на все операции: gateway пускает к backend только с токеном,
 *     в Swagger UI появляется кнопка Authorize.</li>
 * </ul>
 */
public class BackendApiDocsTransformer {
    public static final String SECURITY_SCHEME = "keycloak";
    static final String INTERNAL_PREFIX = "/internal/rest/";
    static final String PUBLIC_PREFIX = "/api/backend/";

    private final JsonMapper jsonMapper;
    private final RoutingProperties.Swagger swagger;

    public BackendApiDocsTransformer(final JsonMapper jsonMapper, final RoutingProperties.Swagger swagger) {
        this.jsonMapper = jsonMapper;
        this.swagger = swagger;
    }

    public String transform(final String apiDocs) {
        final JsonNode root = jsonMapper.readTree(apiDocs);
        if (!(root instanceof ObjectNode document)) {
            return apiDocs;
        }
        document.remove("servers");
        if (document.get("paths") instanceof ObjectNode paths) {
            final ObjectNode publicPaths = jsonMapper.createObjectNode();
            paths.properties().forEach(path -> {
                if (path.getKey().startsWith(INTERNAL_PREFIX)) {
                    publicPaths.set(PUBLIC_PREFIX + path.getKey().substring(INTERNAL_PREFIX.length()), path.getValue());
                }
            });
            document.set("paths", publicPaths);
        }
        if (swagger != null) {
            addKeycloakSecurity(document);
        }
        return jsonMapper.writeValueAsString(document);
    }

    private void addKeycloakSecurity(final ObjectNode document) {
        final ObjectNode authorizationCode = jsonMapper.createObjectNode()
                .put("authorizationUrl", swagger.authorizationUrl())
                .put("tokenUrl", swagger.tokenUrl());
        authorizationCode.putObject("scopes").put("openid", "OpenID Connect");

        final ObjectNode components = document.get("components") instanceof ObjectNode existing
                ? existing
                : document.putObject("components");
        final ObjectNode securitySchemes = components.get("securitySchemes") instanceof ObjectNode existing
                ? existing
                : components.putObject("securitySchemes");
        final ObjectNode keycloak = securitySchemes.putObject(SECURITY_SCHEME).put("type", "oauth2");
        keycloak.putObject("flows").set("authorizationCode", authorizationCode);

        document.putArray("security").addObject().putArray(SECURITY_SCHEME).add("openid");
    }
}
