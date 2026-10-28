package spike.identity;

import java.util.Optional;

/**
 * Where grants live. The spike keeps them in memory. Production needs a store shared by every API task (a database
 * table), because the API runs more than one task; the interface is the same.
 */
public interface GrantStore {

    void save(DelegationGrant grant);

    Optional<DelegationGrant> findByHandleHash(String handleHash);

    Optional<DelegationGrant> findById(String id);

    void replace(DelegationGrant grant);

    /** How many unexpired, unrevoked grants this employee holds right now (for the per-employee cap). */
    long countActive(String employeeSubject, java.time.Instant now);
}
