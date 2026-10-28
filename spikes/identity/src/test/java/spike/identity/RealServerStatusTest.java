package spike.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * MockMvc does not perform the servlet container's error dispatch, which is where a status can be rewritten (a live run
 * showed a 401 becoming a 403). These checks go over real HTTP to a real embedded server.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spike.auth.issuer-uri=" + TestTokens.ISSUER,
        "spike.auth.spa-client-id=" + TestTokens.SPA_CLIENT,
        "spike.auth.agent-client-id=" + TestTokens.AGENT_CLIENT
    })
class RealServerStatusTest {

    private static final TestTokens TOKENS = new TestTokens();

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

    @Value("${local.server.port}")
    int port;

    @Autowired
    GrantService grants;

    private final HttpClient http = HttpClient.newHttpClient();

    private int status(String method, String path, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, HttpRequest.BodyPublishers.noBody());
        for (int index = 0; index < headers.length; index += 2) {
            request.header(headers[index], headers[index + 1]);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void anUnknownHandleAnswers401OverRealHttp() throws Exception {
        assertThat(status("GET", "/api/agent-tools/owner-lookup?lastName=Franklin",
            "Authorization", bearer(TOKENS.agentToken()), "X-Agent-Grant", "pcg_unknown")).isEqualTo(401);
    }

    @Test
    void aRevokedHandleAnswers401OverRealHttp() throws Exception {
        GrantService.Issued issued = grants.create("employee-subject-1", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OWNER_ADMIN")));
        String path = "/api/agent-tools/owner-lookup?lastName=Franklin";
        assertThat(status("GET", path, "Authorization", bearer(TOKENS.agentToken()), "X-Agent-Grant", issued.handle())).isEqualTo(200);
        assertThat(grants.revoke(issued.grant().id(), "employee-subject-1")).isTrue();
        assertThat(status("GET", path, "Authorization", bearer(TOKENS.agentToken()), "X-Agent-Grant", issued.handle())).isEqualTo(401);
    }

    @Test
    void theMachineTokenAloneAnswers403AndAHandleAloneAnswers401OverRealHttp() throws Exception {
        GrantService.Issued issued = grants.create("employee-subject-1", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OWNER_ADMIN")));
        String path = "/api/agent-tools/owner-lookup?lastName=Franklin";
        assertThat(status("GET", path, "Authorization", bearer(TOKENS.agentToken()))).isEqualTo(403);
        assertThat(status("GET", path, "X-Agent-Grant", issued.handle())).isEqualTo(401);
    }

    @Test
    void noCredentialAnswers401AndAnEmployeeTokenWithAHandleAnswers403OverRealHttp() throws Exception {
        String path = "/api/agent-tools/owner-lookup?lastName=Franklin";
        assertThat(status("GET", path)).isEqualTo(401);
        GrantService.Issued issued = grants.create("employee-subject-1", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OWNER_ADMIN")));
        assertThat(status("GET", path, "Authorization", bearer(TOKENS.employeeToken(List.of("OWNER_ADMIN"))), "X-Agent-Grant", issued.handle())).isEqualTo(403);
    }
}
