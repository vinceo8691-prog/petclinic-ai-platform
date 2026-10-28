package spike.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** A secret that is logged is a secret that leaked: run the whole flow at debug level and search every log line. */
@SpringBootTest(properties = {
    "spike.auth.issuer-uri=" + TestTokens.ISSUER,
    "spike.auth.spa-client-id=" + TestTokens.SPA_CLIENT,
    "spike.auth.agent-client-id=" + TestTokens.AGENT_CLIENT
})
@Import(AttackTestSupport.Config.class)
class LogLeakageTest {

    @Autowired
    WebApplicationContext context;

    @Test
    void noHandleAndNoTokenAppearsInAnyLogLineEvenAtDebugLevel() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level previous = root.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);
        try {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            String employeeToken = AttackTestSupport.TOKENS.employeeToken(List.of("OWNER_ADMIN"), Map.of("sub", "employee-" + UUID.randomUUID()));
            String agentToken = AttackTestSupport.TOKENS.agentToken();
            String created = mvc.perform(post("/api/agent-grants").header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andReturn().getResponse().getContentAsString();
            String handle = JsonPath.read(created, "$.handle");
            String grantId = JsonPath.read(created, "$.grantId");
            // success, a refused write, a bad handle, a machine token alone, and a revocation: every kind of event
            mvc.perform(get("/api/agent-tools/owner-lookup?lastName=Franklin").header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken).header("X-Agent-Grant", handle));
            mvc.perform(post("/api/owners-demo").header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken).header("X-Agent-Grant", handle));
            mvc.perform(get("/api/agent-tools/owner-lookup?lastName=Franklin").header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken).header("X-Agent-Grant", "pcg_forged"));
            mvc.perform(get("/api/agent-tools/owner-lookup?lastName=Franklin").header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken));
            mvc.perform(delete("/api/agent-grants/" + grantId).header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken));
            mvc.perform(get("/api/agent-tools/owner-lookup?lastName=Franklin").header(HttpHeaders.AUTHORIZATION, "Bearer " + agentToken).header("X-Agent-Grant", handle));

            List<String> lines = appender.list.stream()
                .map(event -> event.getLoggerName() + " | " + event.getFormattedMessage()
                    + (event.getThrowableProxy() == null ? "" : " | " + event.getThrowableProxy().getMessage()))
                .toList();
            assertThat(lines).as("the test must actually have captured log lines").isNotEmpty();
            Map<String, String> secrets = new java.util.LinkedHashMap<>();
            secrets.put("the grant handle", handle);
            secrets.put("a handle prefix", "pcg_");
            secrets.put("the employee token", employeeToken);
            secrets.put("the agent token", agentToken);
            secrets.put("any JWT header", "eyJ");
            secrets.forEach((label, secret) -> assertThat(lines.stream().filter(line -> line.contains(secret)).toList())
                .as("log lines containing " + label).isEmpty());
        } finally {
            root.detachAppender(appender);
            root.setLevel(previous);
        }
    }
}
