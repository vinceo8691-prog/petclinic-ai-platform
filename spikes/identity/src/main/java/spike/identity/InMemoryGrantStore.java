package spike.identity;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class InMemoryGrantStore implements GrantStore {

    private final Map<String, DelegationGrant> byId = new ConcurrentHashMap<>();
    private final Map<String, String> idByHandleHash = new ConcurrentHashMap<>();

    @Override
    public void save(DelegationGrant grant) {
        byId.put(grant.id(), grant);
        idByHandleHash.put(grant.handleHash(), grant.id());
    }

    @Override
    public Optional<DelegationGrant> findByHandleHash(String handleHash) {
        String id = idByHandleHash.get(handleHash);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<DelegationGrant> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public void replace(DelegationGrant grant) {
        byId.put(grant.id(), grant);
    }

    @Override
    public long countActive(String employeeSubject, java.time.Instant now) {
        return byId.values().stream()
            .filter(grant -> grant.employeeSubject().equals(employeeSubject) && grant.activeAt(now))
            .count();
    }
}
