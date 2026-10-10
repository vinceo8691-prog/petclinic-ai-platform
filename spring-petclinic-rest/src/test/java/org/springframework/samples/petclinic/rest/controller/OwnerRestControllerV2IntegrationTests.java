package org.springframework.samples.petclinic.rest.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.rest.advice.ExceptionControllerAdvice;
import org.springframework.samples.petclinic.rest.controller.v2.OwnerRestControllerV2;
import org.springframework.samples.petclinic.service.clinicService.ApplicationTestConfig;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@code GET /api/v2/owners} through the real controller, service, repository and the in-memory
 * database (the sample data has ten owners), unlike {@link V2RestControllersTests}, which mocks the service.
 */
@SpringBootTest
@ContextConfiguration(classes = ApplicationTestConfig.class)
@WebAppConfiguration
class OwnerRestControllerV2IntegrationTests {

    @Autowired
    private OwnerRestControllerV2 ownerRestControllerV2;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(ownerRestControllerV2)
            .setControllerAdvice(new ExceptionControllerAdvice())
            .build();
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
}
