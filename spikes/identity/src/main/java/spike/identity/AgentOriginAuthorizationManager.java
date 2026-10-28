package spike.identity;

import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * The agent-origin ceiling (ADR-0007, decision 6): separate from the role hierarchy, and deny-only.
 *
 * <ul>
 * <li>An agent acting under a grant may perform only the operations listed in that grant, whatever the roles say.</li>
 * <li>An agent's machine identity without a grant may perform nothing.</li>
 * <li>Anyone else who is authenticated (an employee) passes this check, and method security then decides by role.</li>
 * </ul>
 * Anything not on the allowlist is refused, including every write and every other GET, by default.
 */
public class AgentOriginAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        if (current == null || current instanceof AnonymousAuthenticationToken || !current.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        if (current instanceof DelegatedAuthentication delegated) {
            String operation = context.getRequest().getMethod() + " " + context.getRequest().getRequestURI();
            return new AuthorizationDecision(delegated.grant().allowedOperations().contains(operation));
        }
        if (current instanceof AgentIdentityAuthentication) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(true);
    }
}
