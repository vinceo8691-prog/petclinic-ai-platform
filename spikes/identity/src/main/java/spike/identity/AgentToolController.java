package spike.identity;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The agent's one read tool: a dedicated, read-only owner lookup that returns the least that is needed.
 *
 * <p>Returned: an owner id, a display name, and for each pet its name and type. Never returned: address, city, telephone,
 * or any visit text (visit text is typed by people and is an injection surface).
 *
 * <p>Ambiguity is never resolved by guessing: one match is FOUND, two to five are AMBIGUOUS (candidates are listed so the
 * employee can choose), none is NOT_FOUND, and more than five is TOO_MANY (no candidates, ask for a narrower name).
 */
@RestController
public class AgentToolController {

    static final int MAX_CANDIDATES = 5;

    public record PetSummary(String name, String type) {
    }

    public record OwnerSummary(int ownerId, String name, List<PetSummary> pets) {
    }

    public record LookupResult(String status, List<OwnerSummary> owners) {
    }

    private final OwnerDirectory directory;

    public AgentToolController(OwnerDirectory directory) {
        this.directory = directory;
    }

    @GetMapping("/api/agent-tools/owner-lookup")
    @PreAuthorize("hasRole('OWNER_ADMIN')")
    LookupResult lookup(@RequestParam String lastName, @RequestParam(required = false) String firstName) {
        if (lastName.isBlank() || lastName.length() < 2 || lastName.length() > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lastName must be 2 to 30 characters");
        }
        List<OwnerDirectory.Owner> matches = directory.findByLastNamePrefix(lastName.trim()).stream()
            .filter(owner -> firstName == null || firstName.isBlank()
                || owner.firstName().toLowerCase().startsWith(firstName.trim().toLowerCase()))
            .toList();
        if (matches.isEmpty()) {
            return new LookupResult("NOT_FOUND", List.of());
        }
        if (matches.size() > MAX_CANDIDATES) {
            return new LookupResult("TOO_MANY", List.of());
        }
        List<OwnerSummary> summaries = matches.stream().map(AgentToolController::summarize).toList();
        return new LookupResult(summaries.size() == 1 ? "FOUND" : "AMBIGUOUS", summaries);
    }

    private static OwnerSummary summarize(OwnerDirectory.Owner owner) {
        return new OwnerSummary(owner.id(), owner.firstName() + " " + owner.lastName(),
            owner.pets().stream().map(pet -> new PetSummary(pet.name(), pet.type())).toList());
    }
}
