package ru.smart.factoring.gateway.authorization.service.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Роли realm Keycloak из claim realm_access.roles токена - в authorities ROLE_&lt;роль&gt;.
 */
public class KeycloakRealmRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(final Jwt jwt) {
        if (!(jwt.getClaims().get(REALM_ACCESS_CLAIM) instanceof Map<?, ?> realmAccess)
                || !(realmAccess.get(ROLES) instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .toList();
    }
}
