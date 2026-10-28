package spike.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/** Creates, resolves and revokes delegation grants. The only place a handle is ever seen in clear. */
@Service
public class GrantService {

    /** The one thing an agent may do under a grant in this spike. Fixed on the server; the caller cannot widen it. */
    public static final String OWNER_LOOKUP = "GET /api/agent-tools/owner-lookup";

    private static final String HANDLE_PREFIX = "pcg_";   // makes a leaked handle easy to spot in secret scanning

    private final GrantStore store;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public GrantService(GrantStore store, AuthProperties properties, Clock clock) {
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    /** The result of creating a grant: the secret handle (shown once) and the stored record. */
    public record Issued(String handle, DelegationGrant grant) {
    }

    /** An employee already holds the maximum number of active grants. */
    public static class TooManyGrantsException extends RuntimeException {
        public TooManyGrantsException() {
            super("Too many active grants");
        }
    }

    public Issued create(String employeeSubject, java.util.Collection<? extends GrantedAuthority> employeeAuthorities) {
        if (!properties.agentsAccepted()) {
            throw new IllegalStateException("No agent client is configured");
        }
        Instant now = clock.instant();
        if (store.countActive(employeeSubject, now) >= properties.maxActiveGrantsPerEmployee()) {
            throw new TooManyGrantsException();
        }
        byte[] bytes = new byte[32];   // 256 bits
        random.nextBytes(bytes);
        String handle = HANDLE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Set<String> roles = employeeAuthorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toUnmodifiableSet());
        DelegationGrant grant = new DelegationGrant(
            UUID.randomUUID().toString(), hash(handle), employeeSubject, roles, properties.agentClientId(),
            Set.of(OWNER_LOOKUP), now, now.plus(properties.effectiveGrantTtl()), null);
        store.save(grant);
        return new Issued(handle, grant);
    }

    /**
     * Resolves a handle for a caller. Empty means "no" for every reason (unknown, expired, revoked, or presented by
     * a different agent client than the one it was issued to): the caller is never told which.
     */
    public Optional<DelegationGrant> resolve(String handle, String presentingAgentClientId) {
        if (handle == null || !handle.startsWith(HANDLE_PREFIX) || presentingAgentClientId == null) {
            return Optional.empty();
        }
        return store.findByHandleHash(hash(handle))
            .filter(grant -> grant.activeAt(clock.instant()))
            .filter(grant -> grant.agentClientId().equals(presentingAgentClientId));
    }

    /** Only the employee who created a grant can revoke it. */
    public boolean revoke(String grantId, String employeeSubject) {
        Optional<DelegationGrant> grant = store.findById(grantId).filter(found -> found.employeeSubject().equals(employeeSubject));
        grant.ifPresent(found -> store.replace(found.revokedAt(clock.instant())));
        return grant.isPresent();
    }

    static String hash(String handle) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(handle.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
