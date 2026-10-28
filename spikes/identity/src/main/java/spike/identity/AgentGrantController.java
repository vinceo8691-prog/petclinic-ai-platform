package spike.identity;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where an employee, and only an employee, hands the agent a short-lived read-only permission. The UI calls this with the
 * employee's own token and passes only the resulting handle on to the agent.
 */
@RestController
@RequestMapping("/api/agent-grants")
public class AgentGrantController {

    /**
     * What the employee's UI receives. The handle is a secret: {@code toString} masks it, because Spring MVC logs response
     * bodies at DEBUG level and a log line must never carry it (found by the log leakage test).
     */
    public record GrantResponse(String handle, String grantId, String agentClientId, Set<String> allowedOperations, String expiresAt) {

        @Override
        public String toString() {
            return "GrantResponse[handle=[REDACTED], grantId=" + grantId + ", agentClientId=" + agentClientId
                + ", allowedOperations=" + allowedOperations + ", expiresAt=" + expiresAt + "]";
        }
    }

    private final GrantService grants;

    public AgentGrantController(GrantService grants) {
        this.grants = grants;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER_ADMIN','VET_ADMIN')")
    ResponseEntity<GrantResponse> create(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken employee)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();   // never an agent, never a delegated caller
        }
        GrantService.Issued issued;
        try {
            // Nothing in the request (a body, a parameter, a header) influences the subject, the roles, the operations
            // or the lifetime: all of them come from the verified token and from server configuration.
            issued = grants.create(employee.getToken().getSubject(), employee.getAuthorities());
        } catch (GrantService.TooManyGrantsException exception) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        DelegationGrant grant = issued.grant();
        return ResponseEntity.status(HttpStatus.CREATED).body(new GrantResponse(
            issued.handle(), grant.id(), grant.agentClientId(), grant.allowedOperations(), grant.expiresAt().toString()));
    }

    @DeleteMapping("/{grantId}")
    ResponseEntity<Void> revoke(@PathVariable String grantId, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken employee)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return grants.revoke(grantId, employee.getToken().getSubject())
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }
}
