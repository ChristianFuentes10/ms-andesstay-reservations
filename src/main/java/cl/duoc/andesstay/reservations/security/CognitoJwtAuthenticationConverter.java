package cl.duoc.andesstay.reservations.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Extrae los roles del JWT de Cognito (claim cognito:groups) y los mapea a
 * GrantedAuthorities con prefijo ROLE_.
 *
 * Si el token es valido pero no trae el claim cognito:groups (usuario recien
 * registrado que aun no fue asignado a ningun grupo), se le asigna el rol
 * DEFAULT_ROLE (configurable, por defecto HUESPED). Esto permite que los
 * huespedes recien registrados puedan crear reservas sin un 403.
 *
 * Para asignar rol ADMIN: en Cognito Console > User Pool > Users,
 * seleccionar el usuario y agregarlo al grupo "ADMIN".
 */
@Component
public class CognitoJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String GROUPS_CLAIM = "cognito:groups";
    private static final String ROLE_PREFIX = "ROLE_";

    /** Rol asignado a usuarios sin grupos en Cognito (variable DEFAULT_ROLE). */
    @Value("${security.default-role:HUESPED}")
    private String defaultRole;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractRoles(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private Collection<GrantedAuthority> extractRoles(Jwt jwt) {
        List<String> groups = jwt.getClaimAsStringList(GROUPS_CLAIM);
        if (groups == null || groups.isEmpty()) {
            // Usuario autenticado pero sin grupos asignados: asignar rol por defecto
            return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + defaultRole.toUpperCase()));
        }
        return groups.stream()
                .filter(Objects::nonNull)
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
                .collect(Collectors.toList());
    }
}
