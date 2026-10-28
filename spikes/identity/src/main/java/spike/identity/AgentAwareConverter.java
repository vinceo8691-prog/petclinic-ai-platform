package spike.identity;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Decides what kind of principal a verified token is, from the app client it was issued to (set by Cognito, not by the
 * caller): the agent's client gives a machine identity with no authorities; anything else is treated as an employee,
 * whose roles come from the groups claim.
 */
public class AgentAwareConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final String agentClientId;
    private final CognitoGroupsConverter employees = new CognitoGroupsConverter();

    public AgentAwareConverter(String agentClientId) {
        this.agentClientId = agentClientId;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String clientId = jwt.getClaimAsString("client_id");
        if (agentClientId != null && !agentClientId.isBlank() && agentClientId.equals(clientId)) {
            return new AgentIdentityAuthentication(jwt);
        }
        return employees.convert(jwt);
    }
}
