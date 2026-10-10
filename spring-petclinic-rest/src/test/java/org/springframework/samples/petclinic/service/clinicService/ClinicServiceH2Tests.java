package org.springframework.samples.petclinic.service.clinicService;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs the shared clinic service tests against H2, the database the application uses by default.
 *
 * <p>This matters for owner search and sorting. {@link ClinicServiceSpringDataJpaTests} runs on HSQLDB, whose
 * schema declares {@code last_name} as {@code VARCHAR_IGNORECASE}, so a case-sensitive query would still pass
 * there. H2 compares text case-sensitively, so only these tests prove that the search and the last-name
 * sort ignore case because the queries say so.
 *
 * @see AbstractClinicServiceTests
 */
// Its own in-memory database name, so other H2 test contexts never share (and re-initialise) this one.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:clinic-service;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("h2")
class ClinicServiceH2Tests extends AbstractClinicServiceTests {

    @Autowired
    EntityManager entityManager;

    @Override
    void clearCache() {
        entityManager.clear();
    }
}
