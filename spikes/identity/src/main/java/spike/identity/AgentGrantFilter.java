package spike.identity;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns "a verified agent machine token" plus "a delegation handle" into "employee X acting through the agent".
 *
 * <ul>
 * <li>No handle: the agent's machine identity stays as it is, with no authorities. It can do nothing.</li>
 * <li>A handle with anything other than the agent's machine token (an employee token, or nothing): refused. A handle is
 *     meaningless without the agent's own credential, so neither secret is enough alone.</li>
 * <li>An agent token with a handle that is unknown, expired, revoked or issued to another agent: 401, with one
 *     answer for every cause.</li>
 * </ul>
 */
public class AgentGrantFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Agent-Grant";

    private final GrantService grants;

    public AgentGrantFilter(GrantService grants) {
        this.grants = grants;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        List<String> handles = Collections.list(request.getHeaders(HEADER));
        if (handles.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null || current instanceof AnonymousAuthenticationToken) {
            // A handle with no machine token: not authenticated at all. Let the normal 401 handling answer.
            chain.doFilter(request, response);
            return;
        }
        // Answer with setStatus, never sendError: sendError makes the servlet container re-dispatch to /error, where the
        // authorization rule would turn a 401 into a 403 (found in the live run; MockMvc does not do error dispatch).
        if (!(current instanceof AgentIdentityAuthentication agent)) {
            // An employee (or anyone else) must never be able to use a grant header.
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Optional<DelegationGrant> grant;
        try {
            grant = handles.size() == 1 ? grants.resolve(handles.get(0), agent.clientId()) : Optional.empty();
        } catch (RuntimeException storeFailure) {
            // The grant store is unavailable: deny, and say "try later". Never fall through to access.
            response.setHeader("Retry-After", "5");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        if (grant.isEmpty()) {
            response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new DelegatedAuthentication(grant.get(),
            grant.get().employeeRoles().stream().map(SimpleGrantedAuthority::new).toList()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
