package ru.smart.factoring.gateway.routing.service.swagger;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Переписывает OpenAPI-спецификацию backend под то, как его API видно снаружи через gateway:
 * <ul>
 *     <li>/internal/rest/** -> /api/** - те же пути, что проксирует RoutingConfiguration;</li>
 *     <li>пути вне /internal/rest убираются: через gateway они недоступны;</li>
 *     <li>servers убирается: там внутренний адрес backend (http://backend:8080), Swagger UI без servers
 *     шлёт запросы на тот же адрес, с которого открыт, то есть через gateway.</li>
 * </ul>
 */
public class BackendApiDocsTransformer {
    static final String INTERNAL_PREFIX = "/internal/rest/";
    static final String PUBLIC_PREFIX = "/api/";

    private final JsonMapper jsonMapper;

    public BackendApiDocsTransformer(final JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
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
        return jsonMapper.writeValueAsString(document);
    }
}
