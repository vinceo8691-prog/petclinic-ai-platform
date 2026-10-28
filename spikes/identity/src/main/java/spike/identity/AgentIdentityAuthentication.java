package spike.identity;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Who the agent is: a verified machine token, and nothing more. It carries NO authorities, so by itself it can
 * read nothing and write nothing. It becomes useful only when a valid delegation grant is presented with it.
 */
public class AgentIdentityAuthentication extends AbstractAuthenticationToken {

    private final Jwt token;

    public AgentIdentityAuthentication(Jwt token) {
        super(List.of());
        this.token = token;
        setAuthenticated(true);
    }

    public String clientId() {
        return token.getClaimAsString("client_id");
    }

    @Override
    public Object getCredentials() {
        return token.getTokenValue();
    }

    @Override
    public Object getPrincipal() {
        return clientId();
    }
}
