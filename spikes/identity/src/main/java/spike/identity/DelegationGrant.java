package spike.identity;

import java.time.Instant;
import java.util.Set;

/**
 * A short-lived, backend-controlled permission for one agent to act, read-only, for one employee.
 * The handle that proves it is never stored; only its hash is.
 *
 * @param id                a public identifier for audit and revocation (not a secret)
 * @param handleHash        SHA-256 of the secret handle
 * @param employeeSubject   the Cognito subject of the employee who created the grant
 * @param employeeRoles     the employee's roles when the grant was created, for example ROLE_OWNER_ADMIN
 * @param agentClientId     the only agent client allowed to present this grant
 * @param allowedOperations the only operations an agent may perform under it, as "METHOD /path"
 */
public record DelegationGrant(
    String id,
    String handleHash,
    String employeeSubject,
    Set<String> employeeRoles,
    String agentClientId,
    Set<String> allowedOperations,
    Instant issuedAt,
    Instant expiresAt,
    Instant revokedAt) {

    public DelegationGrant revokedAt(Instant when) {
        return new DelegationGrant(id, handleHash, employeeSubject, employeeRoles, agentClientId, allowedOperations,
            issuedAt, expiresAt, when);
    }

    public boolean activeAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }
}
