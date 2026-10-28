package spike.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Session 3: the attack matrix on delegation grants. Each test is one way an agent, an employee or an outsider might try
 * to misuse a grant, and what must happen instead. Expiry uses a clock the test moves; a store outage is simulated.
 */
@SpringBootTest(properties = {
    "spike.auth.issuer-uri=" + TestTokens.ISSUER,
    "spike.auth.spa-client-id=" + TestTokens.SPA_CLIENT,
    "spike.auth.agent-client-id=" + TestTokens.AGENT_CLIENT,
    "spike.auth.grant-ttl=PT2M",
    "spike.auth.max-active-grants-per-employee=3"
})
@Import(AttackTestSupport.Config.class)
class GrantAttackMatrixTest {

    private static final TestTokens TOKENS = AttackTestSupport.TOKENS;
    private static final String GRANT_HEADER = "X-Agent-Grant";
    private static final String LOOKUP = "/api/agent-tools/owner-lookup?lastName=Franklin";

    @Autowired
    WebApplicationContext context;
    @Autowired
    GrantService grants;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        AttackTestSupport.CLOCK.reset();
        AttackTestSupport.STORE.failing(false);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String newEmployee(String group) {
        return TOKENS.employeeToken(List.of(group), Map.of("sub", "employee-" + UUID.randomUUID()));
    }

    private record Created(String handle, String grantId) {
    }

    private Created create(String employeeToken) throws Exception {
        MvcResult result = mvc.perform(post("/api/agent-grants").header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)))
            .andExpect(status().isCreated()).andReturn();
        String body = result.getResponse().getContentAsString();
        return new Created(JsonPath.read(body, "$.handle"), JsonPath.read(body, "$.grantId"));
    }

    private MockHttpServletRequestBuilder agentCall(String handle) {
        return get(LOOKUP).header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.agentToken())).header(GRANT_HEADER, handle);
    }

    // ------------------------------------------------------------------ EXPIRED

    @Test
    void aGrantWorksUntilTheInstantItExpiresAndNotAfter() throws Exception {
        Created grant = create(newEmployee("OWNER_ADMIN"));
        mvc.perform(agentCall(grant.handle())).andExpect(status().isOk());
        AttackTestSupport.CLOCK.advance(Duration.ofMinutes(2).minusMillis(1));
        mvc.perform(agentCall(grant.handle())).andExpect(status().isOk());       // one millisecond before expiry
        AttackTestSupport.CLOCK.advance(Duration.ofMillis(1));
        mvc.perform(agentCall(grant.handle())).andExpect(status().isUnauthorized());   // exactly at expiry
        AttackTestSupport.CLOCK.advance(Duration.ofHours(1));
        mvc.perform(agentCall(grant.handle())).andExpect(status().isUnauthorized());
    }

    @Test
    void theCallerCannotLengthenTheLifetimeOrWidenTheGrant() throws Exception {
        String employeeToken = newEmployee("OWNER_ADMIN");
        MvcResult result = mvc.perform(post("/api/agent-grants?ttl=PT1H&expiresAt=2099-01-01T00:00:00Z&allowedOperations=GET%20/api/owners-demo&roles=ROLE_ADMIN&employeeSubject=someone-else&agentClientId=evil")
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken))
                .header("X-Employee", "someone-else").header("X-Roles", "ROLE_ADMIN").header("X-Ttl", "PT1H")
                .contentType("application/json").content("{\"employeeSubject\":\"someone-else\",\"roles\":[\"ROLE_ADMIN\"],\"ttl\":\"PT1H\",\"allowedOperations\":[\"POST /api/owners-demo\"]}"))
            .andExpect(status().isCreated()).andReturn();
        String grantId = JsonPath.read(result.getResponse().getContentAsString(), "$.grantId");
        DelegationGrant stored = AttackTestSupport.STORE.findById(grantId).orElseThrow();
        assertThat(stored.employeeSubject()).isEqualTo(TOKENS.subjectOf(employeeToken));       // only from the verified token
        assertThat(stored.employeeRoles()).containsExactly("ROLE_OWNER_ADMIN");                // never ROLE_ADMIN
        assertThat(stored.allowedOperations()).containsExactly("GET /api/agent-tools/owner-lookup");
        assertThat(stored.agentClientId()).isEqualTo(TestTokens.AGENT_CLIENT);
        assertThat(Duration.between(stored.issuedAt(), stored.expiresAt())).isEqualTo(Duration.ofMinutes(2));
    }

    // ------------------------------------------------------------------ REVOKED

    @Test
    void aRevokedGrantIsRefusedAtOnceAndRevokingTwiceIsHarmless() throws Exception {
        String employeeToken = newEmployee("OWNER_ADMIN");
        Created grant = create(employeeToken);
        mvc.perform(agentCall(grant.handle())).andExpect(status().isOk());
        mvc.perform(delete("/api/agent-grants/" + grant.grantId()).header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)))
            .andExpect(status().isNoContent());
        mvc.perform(agentCall(grant.handle())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/agent-grants/" + grant.grantId()).header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)))
            .andExpect(status().isNoContent());
        mvc.perform(agentCall(grant.handle())).andExpect(status().isUnauthorized());
    }

    @Test
    void anotherEmployeeCannotRevokeMyGrant() throws Exception {
        Created grant = create(newEmployee("OWNER_ADMIN"));
        mvc.perform(delete("/api/agent-grants/" + grant.grantId()).header(HttpHeaders.AUTHORIZATION, bearer(newEmployee("OWNER_ADMIN"))))
            .andExpect(status().isNotFound());
        mvc.perform(agentCall(grant.handle())).andExpect(status().isOk());   // still alive
    }

    @Test
    void theAgentCannotRevokeOrInspectGrants() throws Exception {
        Created grant = create(newEmployee("OWNER_ADMIN"));
        mvc.perform(delete("/api/agent-grants/" + grant.grantId())
                .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.agentToken())).header(GRANT_HEADER, grant.handle()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/agent-grants/" + grant.grantId())
                .header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.agentToken())).header(GRANT_HEADER, grant.handle()))
            .andExpect(status().isForbidden());
    }

    @Test
    void revokingAnUnknownGrantIsNotFound() throws Exception {
        mvc.perform(delete("/api/agent-grants/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(newEmployee("OWNER_ADMIN"))))
            .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ MISSING OR MALFORMED

    @Test
    void emptyBlankAndRepeatedGrantHeadersAreRefused() throws Exception {
        Created grant = create(newEmployee("OWNER_ADMIN"));
        mvc.perform(agentCall("")).andExpect(status().isUnauthorized());
        mvc.perform(agentCall("   ")).andExpect(status().isUnauthorized());
        mvc.perform(get(LOOKUP).header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.agentToken()))
                .header(GRANT_HEADER, grant.handle()).header(GRANT_HEADER, grant.handle()))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(LOOKUP).header(HttpHeaders.AUTHORIZATION, bearer(TOKENS.agentToken()))
                .header(GRANT_HEADER, grant.handle()).header(GRANT_HEADER, "pcg_other"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void truncatedAlteredAndSubstitutedHandlesAreRefused() throws Exception {
        Created grant = create(newEmployee("OWNER_ADMIN"));
        String handle = grant.handle();
        List<String> attempts = List.of(
            handle.substring(0, handle.length() - 1),                      // truncated
            handle + "A",                                                  // extended
            handle.toUpperCase(),                                          // case changed
            "xxx_" + handle.substring(4),                                  // other prefix
            handle.substring(4),                                           // prefix removed
            GrantService.hash(handle),                                     // the stored hash is not a handle
            "pcg_" + "A".repeat(10_000),                                   // very long
            " " + handle);                                                 // leading space
        for (String attempt : attempts) {
            mvc.perform(agentCall(attempt)).andExpect(status().isUnauthorized());
        }
        // A control character in a header value never reaches the application: Spring Security's firewall rejects it (400).
        mvc.perform(agentCall(handle + "\u0000")).andExpect(status().isBadRequest());
    }

    @Test
    void randomGuessesNeverSucceed() throws Exception {
        SecureRandom random = new SecureRandom();
        create(newEmployee("OWNER_ADMIN"));   // there is at least one live grant to be guessed at
        for (int attempt = 0; attempt < 100; attempt++) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String guess = "pcg_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            mvc.perform(agentCall(guess)).andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------ MISMATCHED

    @Test
    void aGrantBelongsToExactlyOneAgentClient() throws Exception {
        Created grant = create(newEmployee("OWNER_ADMIN"));
        assertThat(grants.resolve(grant.handle(), TestTokens.AGENT_CLIENT)).isPresent();
        assertThat(grants.resolve(grant.handle(), "some-other-agent")).isEmpty();
        assertThat(grants.resolve(grant.handle(), null)).isEmpty();
        assertThat(grants.resolve(null, TestTokens.AGENT_CLIENT)).isEmpty();
    }

    @Test
    void aValidlySignedTokenFromAClientTheApiDoesNotKnowIsRefused() throws Exception {
        Created grant = create(newEmployee("OWNER_ADMIN"));
        String intruder = TOKENS.agentTokenWith(Map.of("client_id", "intruder-client", "sub", "intruder-client"));
        mvc.perform(get(LOOKUP).header(HttpHeaders.AUTHORIZATION, bearer(intruder)).header(GRANT_HEADER, grant.handle()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void theAgentsMachineTokenIsHeldToTheSameStandardAsAnyToken() throws Exception {
        Created grant = create(newEmployee("OWNER_ADMIN"));
        Instant now = Instant.now();
        List<String> badTokens = List.of(
            TOKENS.agentTokenWith(Map.of("iat", now.minusSeconds(900), "exp", now.minusSeconds(600))),   // expired
            TOKENS.agentTokenWith(Map.of("iss", "https://evil.example/pool")),                           // wrong issuer
            TOKENS.agentTokenWith(Map.of("token_use", "id")),                                            // not an access token
            TOKENS.agentTokenSignedByOtherKey());                                                       // forged signature
        for (String token : badTokens) {
            mvc.perform(get(LOOKUP).header(HttpHeaders.AUTHORIZATION, bearer(token)).header(GRANT_HEADER, grant.handle()))
                .andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------ IMPERSONATION AND PERMISSION LIMITS

    @Test
    void anAgentCannotInventRolesOrAnEmployee() throws Exception {
        Created vetGrant = create(newEmployee("VET_ADMIN"));
        mvc.perform(agentCall(vetGrant.handle()).header("X-Roles", "ROLE_ADMIN").header("X-Employee", "someone-else")
                .param("roles", "ROLE_ADMIN").param("employee", "someone-else"))
            .andExpect(status().isForbidden());   // still only what the VET_ADMIN who created the grant may read: not owners
    }

    @Test
    void aGrantKeepsTheRolesItWasCreatedWithUntilItExpiresOrIsRevoked_knownLimitation() throws Exception {
        // KNOWN LIMITATION (risk R3): the roles are a snapshot. If the employee loses a role afterwards, the grant still
        // carries it until it expires (minutes) or is revoked. The cure in production: revoke the employee's grants on a
        // role change, or reload the roles on each call. The prototype does neither, and this test documents it.
        String employeeToken = newEmployee("OWNER_ADMIN");
        Created grant = create(employeeToken);
        mvc.perform(agentCall(grant.handle())).andExpect(status().isOk());
        // (the employee's role is removed in Cognito here; nothing in the API can know until a new token is issued)
        mvc.perform(agentCall(grant.handle())).andExpect(status().isOk());
        mvc.perform(delete("/api/agent-grants/" + grant.grantId()).header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)))
            .andExpect(status().isNoContent());
        mvc.perform(agentCall(grant.handle())).andExpect(status().isUnauthorized());
    }

    @Test
    void anEmployeeIsCappedAtAFewActiveGrantsAndExpiredOnesDoNotCount() throws Exception {
        String employeeToken = newEmployee("OWNER_ADMIN");
        Created first = create(employeeToken);
        create(employeeToken);
        create(employeeToken);
        mvc.perform(post("/api/agent-grants").header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)))
            .andExpect(status().isTooManyRequests());
        mvc.perform(delete("/api/agent-grants/" + first.grantId()).header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)))
            .andExpect(status().isNoContent());
        create(employeeToken);   // a revoked grant frees a place
        mvc.perform(post("/api/agent-grants").header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)))
            .andExpect(status().isTooManyRequests());
        AttackTestSupport.CLOCK.advance(Duration.ofMinutes(3));
        create(employeeToken);   // expired grants free the places
    }

    @Test
    void oneEmployeesCapDoesNotBlockAnother() throws Exception {
        String busy = newEmployee("OWNER_ADMIN");
        create(busy);
        create(busy);
        create(busy);
        create(newEmployee("OWNER_ADMIN"));
    }

    // ------------------------------------------------------------------ THE STORE FAILS

    @Test
    void whenTheGrantStoreFailsTheAgentIsRefusedNotLetThrough() throws Exception {
        Created grant = create(newEmployee("OWNER_ADMIN"));
        mvc.perform(agentCall(grant.handle())).andExpect(status().isOk());
        AttackTestSupport.STORE.failing(true);
        mvc.perform(agentCall(grant.handle())).andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "5"));
        AttackTestSupport.STORE.failing(false);
        mvc.perform(agentCall(grant.handle())).andExpect(status().isOk());   // and it recovers
    }

    // ------------------------------------------------------------------ CONCURRENCY

    @Test
    void noRequestSucceedsAfterARevocationHasReturned() throws Exception {
        String employeeToken = newEmployee("OWNER_ADMIN");
        Created grant = create(employeeToken);
        AtomicBoolean revoked = new AtomicBoolean(false);
        AtomicInteger violations = new AtomicInteger();
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger refusals = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> workers = new java.util.ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                workers.add(pool.submit(() -> {
                    for (int attempt = 0; attempt < 150; attempt++) {
                        boolean startedAfterRevocation = revoked.get();
                        int code = mvc.perform(agentCall(grant.handle())).andReturn().getResponse().getStatus();
                        if (code == 200) {
                            successes.incrementAndGet();
                            if (startedAfterRevocation) {
                                violations.incrementAndGet();
                            }
                        } else if (code == 401) {
                            refusals.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            Thread.sleep(40);
            mvc.perform(delete("/api/agent-grants/" + grant.grantId()).header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)))
                .andExpect(status().isNoContent());
            revoked.set(true);
            for (Future<?> worker : workers) {
                worker.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(violations.get()).as("requests that started after the revocation completed and still succeeded").isZero();
        assertThat(refusals.get()).isPositive();
    }
}
