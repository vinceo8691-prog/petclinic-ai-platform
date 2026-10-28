package spike.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The key attacks again, over real HTTP to a real embedded server, because MockMvc skips the servlet container's error
 * dispatch (the lesson of finding F17). What the client sees here is what a deployed API would answer.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spike.auth.issuer-uri=" + TestTokens.ISSUER,
        "spike.auth.spa-client-id=" + TestTokens.SPA_CLIENT,
        "spike.auth.agent-client-id=" + TestTokens.AGENT_CLIENT,
        "spike.auth.grant-ttl=PT2M",
        "spike.auth.max-active-grants-per-employee=3"
    })
@Import(AttackTestSupport.Config.class)
class GrantAttackOverHttpTest {

    private static final TestTokens TOKENS = AttackTestSupport.TOKENS;
    private static final String LOOKUP = "/api/agent-tools/owner-lookup?lastName=Franklin";

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        AttackTestSupport.CLOCK.reset();
        AttackTestSupport.STORE.failing(false);
    }

    private record Response(int status, String body, String retryAfter) {
    }

    private Response send(String method, String path, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, HttpRequest.BodyPublishers.noBody());
        for (int index = 0; index < headers.length; index += 2) {
            request.header(headers[index], headers[index + 1]);
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body(), response.headers().firstValue("Retry-After").orElse(null));
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
        Response response = send("POST", "/api/agent-grants", "Authorization", bearer(employeeToken));
        assertThat(response.status()).isEqualTo(201);
        return new Created(JsonPath.read(response.body(), "$.handle"), JsonPath.read(response.body(), "$.grantId"));
    }

    private Response agentLookup(String handle) throws Exception {
        return send("GET", LOOKUP, "Authorization", bearer(TOKENS.agentToken()), "X-Agent-Grant", handle);
    }

    @Test
    void theHappyPathWorksAndAnExpiredGrantIsRefusedOverHttp() throws Exception {
        Created grant = create(newEmployee("OWNER_ADMIN"));
        assertThat(agentLookup(grant.handle()).status()).isEqualTo(200);
        AttackTestSupport.CLOCK.advance(Duration.ofMinutes(3));
        assertThat(agentLookup(grant.handle()).status()).isEqualTo(401);
    }

    @Test
    void aRevokedGrantIsRefusedOverHttp() throws Exception {
        String employeeToken = newEmployee("OWNER_ADMIN");
        Created grant = create(employeeToken);
        assertThat(send("DELETE", "/api/agent-grants/" + grant.grantId(), "Authorization", bearer(employeeToken)).status()).isEqualTo(204);
        assertThat(agentLookup(grant.handle()).status()).isEqualTo(401);
    }

    @Test
    void aStoreOutageAnswers503WithRetryAfterAndRecoversOverHttp() throws Exception {
        Created grant = create(newEmployee("OWNER_ADMIN"));
        AttackTestSupport.STORE.failing(true);
        Response outage = agentLookup(grant.handle());
        assertThat(outage.status()).isEqualTo(503);
        assertThat(outage.retryAfter()).isEqualTo("5");
        assertThat(outage.body()).doesNotContain("Francklin").doesNotContain("George");   // no data leaked while failing
        AttackTestSupport.STORE.failing(false);
        assertThat(agentLookup(grant.handle()).status()).isEqualTo(200);
    }

    @Test
    void emptyAndRepeatedHeadersAndForgedHandlesAreRefusedOverHttp() throws Exception {
        Created grant = create(newEmployee("OWNER_ADMIN"));
        assertThat(agentLookup("").status()).isEqualTo(401);
        assertThat(send("GET", LOOKUP, "Authorization", bearer(TOKENS.agentToken()),
            "X-Agent-Grant", grant.handle(), "X-Agent-Grant", grant.handle()).status()).isEqualTo(401);
        assertThat(agentLookup("pcg_" + "A".repeat(43)).status()).isEqualTo(401);
        assertThat(agentLookup(grant.handle().substring(0, grant.handle().length() - 1)).status()).isEqualTo(401);
    }

    @Test
    void eachSecretAloneAndAnEmployeeWithAHandleAreRefusedOverHttp() throws Exception {
        String employeeToken = newEmployee("OWNER_ADMIN");
        Created grant = create(employeeToken);
        assertThat(send("GET", LOOKUP, "Authorization", bearer(TOKENS.agentToken())).status()).isEqualTo(403);       // machine token alone
        assertThat(send("GET", LOOKUP, "X-Agent-Grant", grant.handle()).status()).isEqualTo(401);                    // handle alone
        assertThat(send("GET", LOOKUP, "Authorization", bearer(employeeToken), "X-Agent-Grant", grant.handle()).status()).isEqualTo(403);
        assertThat(send("GET", LOOKUP).status()).isEqualTo(401);                                                      // nothing at all
    }

    @Test
    void anAgentUnderAGrantCanOnlyReadTheLookupOverHttp() throws Exception {
        Created grant = create(newEmployee("ADMIN"));
        String[][] forbidden = {
            {"POST", "/api/owners-demo"}, {"PUT", "/api/owners-demo"}, {"DELETE", "/api/owners-demo"}, {"PATCH", "/api/owners-demo"},
            {"GET", "/api/owners-demo"}, {"GET", "/api/vets-demo"}, {"GET", "/api/whoami"},
            {"POST", "/api/agent-tools/owner-lookup?lastName=Franklin"}, {"POST", "/api/agent-grants"},
            {"DELETE", "/api/agent-grants/" + grant.grantId()}, {"GET", "/api/agent-tools/owner-lookup/"},
            {"GET", "/api/agent-tools/../owners-demo"}, {"GET", "/api/agent-tools/owner-lookup;x=1?lastName=Franklin"},
            {"GET", "/api//agent-tools/owner-lookup?lastName=Franklin"}, {"GET", "/api/agent-tools/owner-lookup%2e?lastName=Franklin"}};
        for (String[] request : forbidden) {
            Response response = send(request[0], request[1], "Authorization", bearer(TOKENS.agentToken()), "X-Agent-Grant", grant.handle());
            System.out.println("ATTACK-STATUS " + response.status() + " " + request[0] + " " + request[1]);
            // Refused one way or another (the firewall, the ceiling, or the router), never a success and never any data.
            assertThat(response.status()).as(request[0] + " " + request[1]).isIn(400, 401, 403, 404, 405);
            assertThat(response.body()).as("body of " + request[0] + " " + request[1]).doesNotContain("Franklin").doesNotContain("owners\"");
        }
        assertThat(agentLookup(grant.handle()).status()).isEqualTo(200);   // and the one permitted call still works
    }

    @Test
    void theCapOnActiveGrantsAnswers429OverHttp() throws Exception {
        String employeeToken = newEmployee("OWNER_ADMIN");
        create(employeeToken);
        create(employeeToken);
        create(employeeToken);
        assertThat(send("POST", "/api/agent-grants", "Authorization", bearer(employeeToken)).status()).isEqualTo(429);
    }

    @Test
    void anEmployeeWithoutARoleCannotMintAGrantOverHttp() throws Exception {
        assertThat(send("POST", "/api/agent-grants", "Authorization", bearer(TOKENS.employeeToken(List.of()))).status()).isEqualTo(403);
        assertThat(send("POST", "/api/agent-grants").status()).isEqualTo(401);
    }
}
