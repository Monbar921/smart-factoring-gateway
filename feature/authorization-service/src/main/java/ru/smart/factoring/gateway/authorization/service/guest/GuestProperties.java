package ru.smart.factoring.gateway.authorization.service.guest;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Гостевой вход «Запуск демо» (замечания 28.10, П1-2): gateway сам получает у Keycloak токен демо-пользователя,
 * пароль в браузер не попадает.
 *
 * @param enabled      false - кнопки входа нет, POST /api/guest/session отвечает 404
 * @param tokenUri     token endpoint realm стенда по внутренней сети (http://keycloak:8080/realms/…/protocol/openid-connect/token)
 * @param clientId     конфиденциальный клиент realm с Direct Access Grants
 * @param clientSecret секрет клиента
 * @param username     демо-пользователь с ролями operator и manager
 * @param password     его пароль
 */
@ConfigurationProperties("smart-factoring.gateway.guest")
public record GuestProperties(boolean enabled, String tokenUri, String clientId, String clientSecret, String username,
                              String password) {
}
