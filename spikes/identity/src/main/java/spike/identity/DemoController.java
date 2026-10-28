package spike.identity;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Stand-ins for PetClinic endpoints, protected the way the real controllers are. */
@RestController
public class DemoController {

    @GetMapping("/api/whoami")
    Map<String, Object> whoAmI(JwtAuthenticationToken authentication) {
        List<String> roles = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList();
        Object clientId = authentication.getToken().getClaim("client_id");
        return Map.of(
            "subject", authentication.getToken().getSubject(),
            "clientId", String.valueOf(clientId),
            "roles", roles);
    }

    @GetMapping("/api/owners-demo")
    @PreAuthorize("hasRole('OWNER_ADMIN')")
    Map<String, Object> owners() {
        return Map.of("owners", List.of("George Franklin", "Betty Davis"));
    }

    @PostMapping("/api/owners-demo")
    @PreAuthorize("hasRole('OWNER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createOwner() {
        return Map.of("created", true);
    }

    @GetMapping("/api/vets-demo")
    @PreAuthorize("hasRole('VET_ADMIN')")
    Map<String, Object> vets() {
        return Map.of("vets", List.of("James Carter"));
    }
}
