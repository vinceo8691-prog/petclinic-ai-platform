package spike.identity;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Builds locally signed tokens shaped like Cognito access tokens. Test code only; there is no such facility in main code. */
final class TestTokens {

    static final String ISSUER = "https://issuer.test/pool";
    static final String SPA_CLIENT = "spa-client";

    private final KeyPair keyPair;
    private final NimbusJwtEncoder encoder;

    TestTokens() {
        this.keyPair = generateKeyPair();
        this.encoder = encoderFor(keyPair);
    }

    RSAPublicKey publicKey() {
        return (RSAPublicKey) keyPair.getPublic();
    }

    /** A valid employee access token for the given groups; claims in {@code overrides} replace or add to the defaults. */
    String employeeToken(List<String> groups, Map<String, Object> overrides) {
        return sign(encoder, claims(groups, overrides));
    }

    String employeeToken(List<String> groups) {
        return employeeToken(groups, Map.of());
    }

    static final String AGENT_CLIENT = "agent-client";

    /** A machine token shaped like Cognito's client-credentials token: sub equals client id, one scope, no user, no groups. */
    String agentToken() {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", ISSUER);
        claims.put("sub", AGENT_CLIENT);
        claims.put("client_id", AGENT_CLIENT);
        claims.put("token_use", "access");
        claims.put("scope", "petclinic-api/agent.read");
        claims.put("iat", now);
        claims.put("exp", now.plusSeconds(300));
        return sign(encoder, claims);
    }

    /** A machine token with chosen claims replacing the normal ones (for attack tests on the agent's own credential). */
    String agentTokenWith(Map<String, Object> overrides) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", ISSUER);
        claims.put("sub", AGENT_CLIENT);
        claims.put("client_id", AGENT_CLIENT);
        claims.put("token_use", "access");
        claims.put("scope", "petclinic-api/agent.read");
        claims.put("iat", now);
        claims.put("exp", now.plusSeconds(300));
        claims.putAll(overrides);
        return sign(encoder, claims);
    }

    String agentTokenSignedByOtherKey() {
        Instant now = Instant.now();
        return sign(encoderFor(generateKeyPair()), Map.of("iss", ISSUER, "sub", AGENT_CLIENT, "client_id", AGENT_CLIENT,
            "token_use", "access", "scope", "petclinic-api/agent.read", "iat", now, "exp", now.plusSeconds(300)));
    }

    /** Same claims, signed by a different key (an attacker's). */
    String signedByOtherKey(List<String> groups) {
        return sign(encoderFor(generateKeyPair()), claims(groups, Map.of()));
    }

    /** The subject claim of a token this class produced. */
    String subjectOf(String signedToken) {
        String payload = signedToken.split("\\.")[1];
        String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"sub\":\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("no sub claim");
        }
        return matcher.group(1);
    }

    static String unsigned(String signedToken) {
        String[] parts = signedToken.split("\\.");
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        return header + "." + parts[1] + ".";
    }

    static String withTamperedPayload(String signedToken, List<String> newGroups) {
        String[] parts = signedToken.split("\\.");
        String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tampered = json.replaceFirst("\\[[^\\]]*\\]", "[\"" + String.join("\",\"", newGroups) + "\"]");
        return parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(tampered.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
    }

    private static Map<String, Object> claims(List<String> groups, Map<String, Object> overrides) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", ISSUER);
        claims.put("sub", "employee-subject-1");
        claims.put("client_id", SPA_CLIENT);
        claims.put("token_use", "access");
        claims.put("scope", "openid profile");
        claims.put("iat", now);
        claims.put("exp", now.plusSeconds(300));
        if (groups != null) {
            claims.put("cognito:groups", groups);
        }
        claims.putAll(overrides);
        return claims;
    }

    private static String sign(NimbusJwtEncoder encoder, Map<String, Object> claims) {
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder();
        claims.forEach(builder::claim);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, builder.build())).getTokenValue();
    }

    private static NimbusJwtEncoder encoderFor(KeyPair pair) {
        RSAKey key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey(pair.getPrivate()).keyID("test-key").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
