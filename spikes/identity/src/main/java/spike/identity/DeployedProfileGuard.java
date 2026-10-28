package spike.identity;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail closed: under the {@code deployed} profile the application refuses to start when security is
 * disabled or its authentication settings are missing or invalid. Running open must never be what happens
 * when someone forgets a setting.
 */
@Component
@Profile("deployed")
public class DeployedProfileGuard implements InitializingBean {

    private final AuthProperties properties;

    public DeployedProfileGuard(AuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problems = problems(properties);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start in a deployed profile: " + String.join("; ", problems));
        }
    }

    static List<String> problems(AuthProperties properties) {
        List<String> problems = new ArrayList<>();
        if (!properties.enabled()) {
            problems.add("spike.auth.enabled is false");
        }
        String issuer = properties.issuerUri();
        if (issuer == null || issuer.isBlank()) {
            problems.add("spike.auth.issuer-uri is missing");
        } else if (!issuer.startsWith("https://")) {
            problems.add("spike.auth.issuer-uri must be an https URL");
        }
        if (properties.spaClientId() == null || properties.spaClientId().isBlank()) {
            problems.add("spike.auth.spa-client-id is missing");
        }
        return problems;
    }
}
