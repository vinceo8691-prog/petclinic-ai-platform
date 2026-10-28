package spike.identity;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    // ADR-0006: ROLE_ADMIN outranks the two resource-scoped roles.
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
            ROLE_ADMIN > ROLE_OWNER_ADMIN
            ROLE_ADMIN > ROLE_VET_ADMIN
            """);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AuthProperties properties, GrantService grants) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (!properties.enabled()) {
            // Explicit local opt-out only. The deployed profile refuses to start in this state.
            http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(requests -> requests
                    .requestMatchers("/actuator/health").permitAll()
                    // Everything else goes through the agent-origin ceiling, then method security by role.
                    .anyRequest().access(new AgentOriginAuthorizationManager()))
                .oauth2ResourceServer(server -> server
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(new AgentAwareConverter(properties.agentClientId()))))
                .addFilterAfter(new AgentGrantFilter(grants), BearerTokenAuthenticationFilter.class);
        }
        return http.build();
    }

    /**
     * The key set is fetched lazily on the first token, so an unreachable issuer rejects tokens (it never lets
     * them through). Startup configuration is checked separately by the deployed-profile guard.
     */
    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties) {
        String issuer = properties.issuerUri();
        if (!properties.enabled() || issuer == null || issuer.isBlank()) {
            // No issuer configured: a decoder that rejects everything, never one that accepts anything.
            return token -> {
                throw new BadJwtException("Authentication is not configured");
            };
        }
        Set<String> acceptedClients = new LinkedHashSet<>();
        acceptedClients.add(properties.spaClientId());
        if (properties.agentsAccepted()) {
            acceptedClients.add(properties.agentClientId());
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
        decoder.setJwtValidator(TokenValidators.accessToken(issuer, acceptedClients));
        return decoder;
    }
}
