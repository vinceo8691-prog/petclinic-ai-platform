package spike.identity;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Employee authentication as the API sees it: real Spring Security filter chain, real converter, real validators,
 * real role hierarchy and method security, with tokens signed by a local test key instead of Cognito's.
 */
@SpringBootTest(properties = {
    "spike.auth.issuer-uri=" + TestTokens.ISSUER,
    "spike.auth.spa-client-id=" + TestTokens.SPA_CLIENT
})
class EmployeeAuthenticationTest {

    private static final TestTokens TOKENS = new TestTokens();

    @TestConfiguration
    static class LocalKeyDecoder {
        @Bean
        @Primary
        JwtDecoder testDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TOKENS.publicKey()).build();
            decoder.setJwtValidator(TokenValidators.employeeAccessToken(TestTokens.ISSUER, TestTokens.SPA_CLIENT));
            return decoder;
        }
    }

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ------------------------------------------------------------------ credentials that must be rejected (401)

    @Test
    void noCredentialIsRejected() throws Exception {
        mvc.perform(get("/api/whoami")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/owners-demo")).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        mvc.perform(get("/api/whoami").header("Authorization", bearer("not-a-token"))).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String expired = TOKENS.employeeToken(List.of("ADMIN"), Map.of(
            "iat", Instant.now().minusSeconds(900), "exp", Instant.now().minusSeconds(600)));
        mvc.perform(get("/api/whoami").header("Authorization", bearer(expired))).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        String foreign = TOKENS.employeeToken(List.of("ADMIN"), Map.of("iss", "https://evil.example/pool"));
        mvc.perform(get("/api/whoami").header("Authorization", bearer(foreign))).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenIssuedToAnotherAppClientIsRejected() throws Exception {
        String other = TOKENS.employeeToken(List.of("ADMIN"), Map.of("client_id", "some-other-client"));
        mvc.perform(get("/api/whoami").header("Authorization", bearer(other))).andExpect(status().isUnauthorized());
    }

    @Test
    void idTokenIsRejectedAsAnAccessToken() throws Exception {
        String idToken = TOKENS.employeeToken(List.of("ADMIN"), Map.of("token_use", "id"));
        mvc.perform(get("/api/whoami").header("Authorization", bearer(idToken))).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithTamperedPayloadIsRejected() throws Exception {
        String valid = TOKENS.employeeToken(List.of("VET_ADMIN"));
        String tampered = TestTokens.withTamperedPayload(valid, List.of("ADMIN"));
        mvc.perform(get("/api/owners-demo").header("Authorization", bearer(tampered))).andExpect(status().isUnauthorized());
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() throws Exception {
        String unsigned = TestTokens.unsigned(TOKENS.employeeToken(List.of("ADMIN")));
        mvc.perform(get("/api/owners-demo").header("Authorization", bearer(unsigned))).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedByAnotherKeyIsRejected() throws Exception {
        String forged = TOKENS.signedByOtherKey(List.of("ADMIN"));
        mvc.perform(get("/api/owners-demo").header("Authorization", bearer(forged))).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ roles from groups, with the ADR-0006 hierarchy

    @Test
    void adminAloneSatisfiesBothResourceRolesThroughTheHierarchy() throws Exception {
        String token = TOKENS.employeeToken(List.of("ADMIN"));
        mvc.perform(get("/api/whoami").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));
        mvc.perform(get("/api/owners-demo").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/vets-demo").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(post("/api/owners-demo").header("Authorization", bearer(token))).andExpect(status().isCreated());
    }

    @Test
    void ownerAdminCanUseOwnersButNotVets() throws Exception {
        String token = TOKENS.employeeToken(List.of("OWNER_ADMIN"));
        mvc.perform(get("/api/owners-demo").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(post("/api/owners-demo").header("Authorization", bearer(token))).andExpect(status().isCreated());
        mvc.perform(get("/api/vets-demo").header("Authorization", bearer(token))).andExpect(status().isForbidden());
    }

    @Test
    void vetAdminCanUseVetsButNotOwners() throws Exception {
        String token = TOKENS.employeeToken(List.of("VET_ADMIN"));
        mvc.perform(get("/api/vets-demo").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/owners-demo").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mvc.perform(post("/api/owners-demo").header("Authorization", bearer(token))).andExpect(status().isForbidden());
    }

    @Test
    void anAuthenticatedUserWithNoGroupsHasNoRoles() throws Exception {
        String token = TOKENS.employeeToken(List.of());
        mvc.perform(get("/api/whoami").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roles").isEmpty());
        mvc.perform(get("/api/owners-demo").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        mvc.perform(get("/api/vets-demo").header("Authorization", bearer(token))).andExpect(status().isForbidden());
    }

    @Test
    void aMissingGroupsClaimMeansNoRoles() throws Exception {
        String token = TOKENS.employeeToken(null);
        mvc.perform(get("/api/owners-demo").header("Authorization", bearer(token))).andExpect(status().isForbidden());
    }

    @Test
    void anUnknownOrMiscasedGroupGrantsNothing() throws Exception {
        String unknown = TOKENS.employeeToken(List.of("SUPERUSER", "admin", "ROLE_ADMIN"));
        mvc.perform(get("/api/whoami").header("Authorization", bearer(unknown)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roles").isEmpty());
        mvc.perform(get("/api/owners-demo").header("Authorization", bearer(unknown))).andExpect(status().isForbidden());
    }

    @Test
    void aMalformedGroupsClaimGrantsNothing() throws Exception {
        String token = TOKENS.employeeToken(List.of(), Map.of("cognito:groups", "ADMIN"));
        mvc.perform(get("/api/owners-demo").header("Authorization", bearer(token))).andExpect(status().isForbidden());
    }
}
