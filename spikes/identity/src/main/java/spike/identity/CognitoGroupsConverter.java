package spike.identity;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Maps Cognito groups to the three PetClinic roles. Only the three known group names count; a missing,
 * malformed or unknown group claim yields NO roles (fail closed).
 */
public class CognitoGroupsConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String GROUPS_CLAIM = "cognito:groups";
    static final Set<String> KNOWN_GROUPS = Set.of("ADMIN", "OWNER_ADMIN", "VET_ADMIN");

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authoritiesFrom(jwt), jwt.getSubject());
    }

    static Collection<GrantedAuthority> authoritiesFrom(Jwt jwt) {
        Object claim = jwt.getClaim(GROUPS_CLAIM);
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        if (claim instanceof List<?> groups) {
            for (Object group : groups) {
                if (group instanceof String name && KNOWN_GROUPS.contains(name)) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + name));
                }
            }
        }
        return authorities;
    }
}
