package spike.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Fail closed: a deployed profile must refuse to start unless authentication is on and fully configured. */
class DeployedProfileGuardTest {

    @Configuration
    @EnableConfigurationProperties(AuthProperties.class)
    @Import(DeployedProfileGuard.class)
    static class GuardOnly {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(GuardOnly.class);

    private static final String VALID_ISSUER = "spike.auth.issuer-uri=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_example";
    private static final String VALID_CLIENT = "spike.auth.spa-client-id=client-1";

    @Test
    void deployedWithCompleteSettingsStarts() {
        runner.withPropertyValues("spring.profiles.active=deployed", VALID_ISSUER, VALID_CLIENT)
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void deployedWithoutAnyAuthenticationSettingsRefusesToStart() {
        runner.withPropertyValues("spring.profiles.active=deployed")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("spike.auth.issuer-uri is missing")
                    .hasStackTraceContaining("spike.auth.spa-client-id is missing");
            });
    }

    @Test
    void deployedWithSecurityDisabledRefusesToStart() {
        runner.withPropertyValues("spring.profiles.active=deployed", "spike.auth.enabled=false", VALID_ISSUER, VALID_CLIENT)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("spike.auth.enabled is false");
            });
    }

    @Test
    void deployedWithABlankIssuerRefusesToStart() {
        runner.withPropertyValues("spring.profiles.active=deployed", "spike.auth.issuer-uri=   ", VALID_CLIENT)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void deployedWithAnInsecureIssuerRefusesToStart() {
        runner.withPropertyValues("spring.profiles.active=deployed", "spike.auth.issuer-uri=http://insecure.example/pool", VALID_CLIENT)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("must be an https URL");
            });
    }

    @Test
    void deployedWithoutAClientIdRefusesToStart() {
        runner.withPropertyValues("spring.profiles.active=deployed", VALID_ISSUER)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void theDefaultIsSecurityOnNotOff() {
        runner.run(context -> assertThat(context.getBean(AuthProperties.class).enabled()).isTrue());
    }

    @Test
    void anExplicitLocalSetupMayRunWithoutSettings() {
        runner.withPropertyValues("spike.auth.enabled=false")
            .run(context -> assertThat(context).hasNotFailed());
    }
}
