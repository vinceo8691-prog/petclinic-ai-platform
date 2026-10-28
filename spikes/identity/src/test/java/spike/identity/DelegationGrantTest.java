package spike.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Session 2: the delegation grant (Option 1b) works, and the first obvious ways to misuse it do not. The exhaustive
 * attack matrix (expired, revoked, mismatched, impersonation, over-permission) is Session 3.
 */
@SpringBootTest(properties = {
    "spike.auth.issuer-uri=" + TestTokens.ISSUER,
    "spike.auth.spa-client-id=" + TestTokens.SPA_CLIENT,
    "spike.auth.agent-client-id=" + TestTokens.AGENT_CLIENT,
    // these tests share one employee subject across many grants; the cap itself is tested in GrantAttackMatrixTest
    "spike.auth.max-active-grants-per-employee=1000"
})
class DelegationGrantTest {

    private static final TestTokens TOKENS = new TestTokens();
    private static final String GRANT_HEADER = "X-Agent-Grant";

    @TestConfiguration
    static class LocalKeyDecoder {
        @Bean
        @Primary
        JwtDecoder testDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TOKENS.publicKey()).build();
            decoder.setJwtValidator(TokenValidators.accessToken(TestTokens.ISSUER, Set.of(TestTokens.SPA_CLIENT, TestTokens.AGENT_CLIENT)));
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

    /** The UI's step: the employee, with their own token, asks for a grant and gets only a handle. */
    private String employeeCreatesGrant(String employeeToken) throws Exception {
        MvcResult result = mvc.perform(post("/api/agent-grants").header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)))
            .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.handle");
    }

    private MockHttpServletRequestBuilder agentLookup(String handle, String query) {
        return get("/api/agent-tools/owner-lookup?" + query)
            .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.agentToken())).header(GRANT_HEADER, handle);
    }

    // ------------------------------------------------------------------ the grant, as the employee sees it

    @Test
    void anEmployeeGetsAHandleAndNeverAToken() throws Exception {
        String employeeToken = TOKENS.employeeToken(List.of("OWNER_ADMIN"));
        MvcResult result = mvc.perform(post("/api/agent-grants").header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.handle").value(org.hamcrest.Matchers.startsWith("pcg_")))
            .andExpect(jsonPath("$.allowedOperations[0]").value("GET /api/agent-tools/owner-lookup"))
            .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(employeeToken);
        assertThat(body).doesNotContain("eyJ");   // nothing that looks like a JWT
        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        assertThat(expiresAt).isBefore(Instant.now().plusSeconds(301));   // minutes, never longer than five
    }

    @Test
    void theGrantRecordKeepsOnlyAHashOfTheHandle() throws Exception {
        String handle = employeeCreatesGrant(TOKENS.employeeToken(List.of("OWNER_ADMIN")));
        InMemoryGrantStore store = context.getBean(InMemoryGrantStore.class);
        DelegationGrant stored = store.findByHandleHash(GrantService.hash(handle)).orElseThrow();
        assertThat(stored.handleHash()).isNotEqualTo(handle).hasSize(64);
        assertThat(stored.toString()).doesNotContain(handle);
    }

    // ------------------------------------------------------------------ the agent with a grant: a read-only lookup

    @Test
    void theAgentFindsOneOwnerAndGetsOnlyMinimalData() throws Exception {
        String handle = employeeCreatesGrant(TOKENS.employeeToken(List.of("OWNER_ADMIN")));
        MvcResult result = mvc.perform(agentLookup(handle, "lastName=Franklin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FOUND"))
            .andExpect(jsonPath("$.owners[0].name").value("George Franklin"))
            .andExpect(jsonPath("$.owners[0].pets[0].name").value("Leo"))
            .andExpect(jsonPath("$.owners[0].pets[0].type").value("cat"))
            .andReturn();
        String body = result.getResponse().getContentAsString();
        // Exactly these fields, and nothing from the richer record behind them.
        assertThat(JsonPath.<java.util.Map<String, Object>>read(body, "$.owners[0]").keySet()).containsExactlyInAnyOrder("ownerId", "name", "pets");
        assertThat(JsonPath.<java.util.Map<String, Object>>read(body, "$.owners[0].pets[0]").keySet()).containsExactlyInAnyOrder("name", "type");
        assertThat(body).doesNotContain("Liberty").doesNotContain("Madison").doesNotContain("6085551023").doesNotContain("rabies");
    }

    @Test
    void visitTextThatLooksLikeAnInstructionNeverReachesTheAgent() throws Exception {
        String handle = employeeCreatesGrant(TOKENS.employeeToken(List.of("OWNER_ADMIN")));
        MvcResult result = mvc.perform(agentLookup(handle, "lastName=Davis&firstName=Harold"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FOUND")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Ignore previous instructions");
    }

    @Test
    void ambiguityIsReportedNeverGuessed() throws Exception {
        String handle = employeeCreatesGrant(TOKENS.employeeToken(List.of("OWNER_ADMIN")));
        // A last-name PREFIX search: "Davis" matches Betty Davis, Harold Davis and also Davina Davison.
        mvc.perform(agentLookup(handle, "lastName=Davis"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("AMBIGUOUS"))
            .andExpect(jsonPath("$.owners.length()").value(3));
        mvc.perform(agentLookup(handle, "lastName=Dav"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("TOO_MANY"))
            .andExpect(jsonPath("$.owners").isEmpty());
        mvc.perform(agentLookup(handle, "lastName=Nobody"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOT_FOUND"));
        mvc.perform(agentLookup(handle, "lastName=Davis&firstName=Betty"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FOUND"));
    }

    // ------------------------------------------------------------------ neither secret is enough alone

    @Test
    void theAgentsMachineTokenAloneCanDoNothing() throws Exception {
        mvc.perform(get("/api/agent-tools/owner-lookup?lastName=Franklin")
                .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.agentToken())))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/owners-demo").header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.agentToken())))
            .andExpect(status().isForbidden());
    }

    @Test
    void aHandleAloneIsUseless() throws Exception {
        String handle = employeeCreatesGrant(TOKENS.employeeToken(List.of("OWNER_ADMIN")));
        mvc.perform(get("/api/agent-tools/owner-lookup?lastName=Franklin").header(GRANT_HEADER, handle))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anEmployeeCannotUseAHandle() throws Exception {
        String employeeToken = TOKENS.employeeToken(List.of("OWNER_ADMIN"));
        String handle = employeeCreatesGrant(employeeToken);
        mvc.perform(get("/api/agent-tools/owner-lookup?lastName=Franklin")
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)).header(GRANT_HEADER, handle))
            .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ the agent cannot grow its own authority

    @Test
    void theAgentCannotCreateGrantsEvenWithAValidOne() throws Exception {
        String handle = employeeCreatesGrant(TOKENS.employeeToken(List.of("OWNER_ADMIN")));
        mvc.perform(post("/api/agent-grants")
                .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.agentToken())).header(GRANT_HEADER, handle))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/agent-grants").header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.agentToken())))
            .andExpect(status().isForbidden());
    }

    @Test
    void anAgentUnderAGrantCannotWriteAndCannotReadAnythingElse() throws Exception {
        String handle = employeeCreatesGrant(TOKENS.employeeToken(List.of("ADMIN")));   // the strongest employee
        List<MockHttpServletRequestBuilder> forbidden = new ArrayList<>(List.of(
            post("/api/owners-demo"), put("/api/owners-demo"), delete("/api/owners-demo"),
            get("/api/owners-demo"), get("/api/vets-demo"), get("/api/whoami"),
            post("/api/agent-tools/owner-lookup?lastName=Franklin"),
            delete("/api/agent-grants/anything")));
        for (MockHttpServletRequestBuilder request : forbidden) {
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.agentToken())).header(GRANT_HEADER, handle))
                .andExpect(status().isForbidden());
        }
    }

    // ------------------------------------------------------------------ the employee's own limits still apply

    @Test
    void aVetAdminsGrantCannotReadOwners() throws Exception {
        String handle = employeeCreatesGrant(TOKENS.employeeToken(List.of("VET_ADMIN")));
        mvc.perform(agentLookup(handle, "lastName=Franklin")).andExpect(status().isForbidden());
    }

    @Test
    void anAdminsGrantWorksThroughTheHierarchy() throws Exception {
        String handle = employeeCreatesGrant(TOKENS.employeeToken(List.of("ADMIN")));
        mvc.perform(agentLookup(handle, "lastName=Franklin")).andExpect(status().isOk());
    }

    @Test
    void anEmployeeWithNoRolesCannotCreateAGrant() throws Exception {
        mvc.perform(post("/api/agent-grants").header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.employeeToken(List.of()))))
            .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownHandleIsRefusedWithoutSayingWhy() throws Exception {
        mvc.perform(agentLookup("pcg_notarealhandle", "lastName=Franklin")).andExpect(status().isUnauthorized());
        mvc.perform(agentLookup("garbage", "lastName=Franklin")).andExpect(status().isUnauthorized());
    }
}
