package spike.identity;

import java.util.Collection;
import java.util.Set;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

/** Validators for Cognito access tokens: issuer and expiry, token use, and which app client the token was issued to. */
final class TokenValidators {

    private TokenValidators() {
    }

    /** Accepts access tokens issued by the pool to one of the listed app clients (the SPA, and the agent if configured). */
    static OAuth2TokenValidator<Jwt> accessToken(String issuer, Collection<String> acceptedClientIds) {
        Set<String> accepted = Set.copyOf(acceptedClientIds);
        return new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            claimEquals("token_use", "access"),
            // Cognito access tokens carry client_id, not aud, so the audience check is a client check.
            jwt -> accepted.contains(jwt.getClaimAsString("client_id"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token was not issued to an accepted client", null)));
    }

    /** Kept for the employee-only tests: the SPA is the only accepted client. */
    static OAuth2TokenValidator<Jwt> employeeAccessToken(String issuer, String spaClientId) {
        return accessToken(issuer, Set.of(spaClientId));
    }

    static OAuth2TokenValidator<Jwt> claimEquals(String claimName, String expected) {
        return jwt -> {
            Object actual = jwt.getClaim(claimName);
            if (expected != null && expected.equals(actual)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Claim " + claimName + " is not accepted", null));
        };
    }
}
