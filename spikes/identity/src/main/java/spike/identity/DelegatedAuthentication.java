package spike.identity;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * "Employee X, acting through agent Y, for this grant." The authorities are the EMPLOYEE's roles as recorded in the
 * grant when the employee created it (never anything the agent supplied); the allowed operations are the grant's own.
 * Both limits apply: the roles decide what the employee may read, the allowlist decides what an agent may ever do.
 */
public class DelegatedAuthentication extends AbstractAuthenticationToken {

    private final DelegationGrant grant;

    public DelegatedAuthentication(DelegationGrant grant, Collection<? extends GrantedAuthority> employeeAuthorities) {
        super(employeeAuthorities);
        this.grant = grant;
        setAuthenticated(true);
    }

    public DelegationGrant grant() {
        return grant;
    }

    @Override
    public Object getCredentials() {
        return null;   // the handle is never kept after the lookup
    }

    @Override
    public Object getPrincipal() {
        return grant.employeeSubject();
    }
}
