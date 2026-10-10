package org.springframework.samples.petclinic.rest.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.service.clinicService.ApplicationTestConfig;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the owner endpoints through the real application context (controller proxies, method validation,
 * the exception advice), service, repository and the in-memory database (the sample data has ten owners), unlike {@link V2RestControllersTests},
 * which mocks the service. It runs on H2, the application's default database, whose text comparison is
 * case-sensitive, so the case-insensitive search and sort are really exercised (HSQLDB, used by most other
 * tests, declares last names case-insensitive in its schema).
 */
// Each H2 test class names its own in-memory database; two contexts sharing the default name would run the schema twice.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:owner-v2-integration;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("h2")
@ContextConfiguration(classes = ApplicationTestConfig.class)
@WebAppConfiguration
@Transactional // rolls back owners created or changed by a test
class OwnerRestControllerV2IntegrationTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void listsOwnersInIdOrderByDefault() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners?size=3").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.content[1].id").value(2))
            .andExpect(jsonPath("$.content[2].id").value(3))
            .andExpect(jsonPath("$.totalElements").value(10));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void sortsByIdDescending() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners?sort=id&direction=desc&size=3").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(10))
            .andExpect(jsonPath("$.content[1].id").value(9))
            .andExpect(jsonPath("$.content[2].id").value(8));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void sortsByLastNameAscendingWithTiesBrokenOnFirstName() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners?sort=lastName&direction=asc&size=4").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].lastName").value("Black"))
            .andExpect(jsonPath("$.content[1].lastName").value("Coleman"))
            .andExpect(jsonPath("$.content[2].lastName").value("Davis"))
            .andExpect(jsonPath("$.content[2].firstName").value("Betty"))
            .andExpect(jsonPath("$.content[3].lastName").value("Davis"))
            .andExpect(jsonPath("$.content[3].firstName").value("Harold"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void sortsByLastNameDescendingAsTheReverseOfAscending() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners?sort=lastName&direction=desc&size=4").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].lastName").value("Schroeder"))
            .andExpect(jsonPath("$.content[1].lastName").value("Rodriquez"))
            .andExpect(jsonPath("$.content[2].lastName").value("McTavish"))
            .andExpect(jsonPath("$.content[3].lastName").value("Franklin"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void sortsTheSecondPageOfTheLastNameOrder() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners?sort=lastName&page=1&size=4").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.content[0].lastName").value("Escobito"))
            .andExpect(jsonPath("$.content[1].lastName").value("Estaban"))
            .andExpect(jsonPath("$.content[2].lastName").value("Franklin"))
            .andExpect(jsonPath("$.content[3].lastName").value("McTavish"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void sortsOnlyTheOwnersMatchingTheSearch() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners?lastName=Davis&sort=lastName&direction=desc").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].firstName").value("Harold"))
            .andExpect(jsonPath("$.content[1].firstName").value("Betty"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void searchIgnoresCaseOnTheV2Endpoint() throws Exception {
        for (String search : new String[] {"davis", "DAVIS", "dAv"}) {
            this.mockMvc.perform(get("/api/v2/owners").param("lastName", search).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].lastName").value("Davis"));
        }
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void searchIgnoresCaseOnTheV1Endpoint() throws Exception {
        this.mockMvc.perform(get("/api/owners").param("lastName", "davis").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].lastName").value("Davis"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void aPercentSignIsNotAWildcardOnTheV2Endpoint() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners").param("lastName", "%").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void anUnderscoreIsNotAWildcardOnTheV2Endpoint() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners").param("lastName", "D_vis").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void wildcardsAreNotWildcardsOnTheV1EndpointEither() throws Exception {
        // Nothing matches, and the v1 endpoint answers 404 when the search finds no owner.
        this.mockMvc.perform(get("/api/owners").param("lastName", "%").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
        this.mockMvc.perform(get("/api/owners").param("lastName", "D_vis").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void aPageSizeBelowTheMinimumIsABadRequest() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners?size=0").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("ConstraintViolationException"))
            .andExpect(jsonPath("$.schemaValidationErrors[0].field").value("size"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void aPageSizeAboveTheMaximumIsABadRequest() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners?size=101").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.schemaValidationErrors[0].field").value("size"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void aNegativePageIsABadRequest() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners?page=-1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.schemaValidationErrors[0].field").value("page"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void aPageThatIsNotANumberIsABadRequest() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners?page=abc").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("MethodArgumentTypeMismatchException"))
            .andExpect(jsonPath("$.schemaValidationErrors[0].field").value("page"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void anUnknownSortFieldIsABadRequest() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners?sort=bogus").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.schemaValidationErrors[0].field").value("sort"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void aSortFieldThatIsAPropertyNameButNotWhitelistedIsABadRequest() throws Exception {
        // firstName is a real Owner property; only id and lastName may be sorted on.
        this.mockMvc.perform(get("/api/v2/owners?sort=firstName").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
        this.mockMvc.perform(get("/api/v2/owners?sort=id,desc").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void anUnknownDirectionIsABadRequest() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners?direction=sideways").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.schemaValidationErrors[0].field").value("direction"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void theBoundaryValuesAreStillAccepted() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners?page=0&size=1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        this.mockMvc.perform(get("/api/v2/owners?page=0&size=100").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    private static String ownerJson(String telephone) {
        return """
            {"firstName":"Sam","lastName":"Smith","address":"1 Main St.","city":"Madison","telephone":"%s"}
            """.formatted(telephone);
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void creatingAnOwnerWithAnElevenDigitTelephoneIsABadRequest() throws Exception {
        this.mockMvc.perform(post("/api/owners").contentType(MediaType.APPLICATION_JSON).content(ownerJson("60855510231")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.schemaValidationErrors[0].field").value("telephone"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void creatingAnOwnerWithAShortTelephoneIsABadRequest() throws Exception {
        this.mockMvc.perform(post("/api/owners").contentType(MediaType.APPLICATION_JSON).content(ownerJson("5551234")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.schemaValidationErrors[0].field").value("telephone"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void creatingAnOwnerWithATelephoneThatIsNotAllDigitsIsABadRequest() throws Exception {
        this.mockMvc.perform(post("/api/owners").contentType(MediaType.APPLICATION_JSON).content(ownerJson("608-555-10")))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void creatingAnOwnerWithATenDigitTelephoneSucceeds() throws Exception {
        this.mockMvc.perform(post("/api/owners").contentType(MediaType.APPLICATION_JSON).content(ownerJson("6085551234")))
            .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void updatingAnOwnerWithAnElevenDigitTelephoneIsABadRequest() throws Exception {
        this.mockMvc.perform(put("/api/owners/1").contentType(MediaType.APPLICATION_JSON).content(ownerJson("60855510231")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.schemaValidationErrors[0].field").value("telephone"));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void updatingAnOwnerWithATenDigitTelephoneSucceeds() throws Exception {
        this.mockMvc.perform(put("/api/owners/1").contentType(MediaType.APPLICATION_JSON).content(ownerJson("6085551234")))
            .andExpect(status().isNoContent());
    }
}
