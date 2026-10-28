package spike.identity;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Authentication settings. Security is ON unless a local setup turns it off explicitly: the default is the
 * safe direction (the inverse of the production backend's {@code petclinic.security.enable=false}).
 *
 * @param agentClientId the Cognito app client of the agent's machine identity; blank means agents are not accepted
 * @param grantTtl      how long a delegation grant lives; never longer than {@link #MAX_GRANT_TTL}
 */
@ConfigurationProperties("spike.auth")
public record AuthProperties(
    @DefaultValue("true") boolean enabled,
    String issuerUri,
    String spaClientId,
    String agentClientId,
    @DefaultValue("PT2M") Duration grantTtl,
    @DefaultValue("10") int maxActiveGrantsPerEmployee) {

    /** A grant is minutes, never longer (Cognito's own shortest access token is 5 minutes; a grant can be shorter). */
    public static final Duration MAX_GRANT_TTL = Duration.ofMinutes(5);

    public Duration effectiveGrantTtl() {
        if (grantTtl == null || grantTtl.isNegative() || grantTtl.isZero()) {
            return Duration.ofMinutes(2);
        }
        return grantTtl.compareTo(MAX_GRANT_TTL) > 0 ? MAX_GRANT_TTL : grantTtl;
    }

    public boolean agentsAccepted() {
        return agentClientId != null && !agentClientId.isBlank();
    }
}
