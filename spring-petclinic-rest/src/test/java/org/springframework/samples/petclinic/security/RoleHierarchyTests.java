package org.springframework.samples.petclinic.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.model.Vet;
import org.springframework.samples.petclinic.rest.advice.ExceptionControllerAdvice;
import org.springframework.samples.petclinic.rest.controller.v1.VetRestControllerV1;
import org.springframework.samples.petclinic.service.SpecialtyService;
import org.springframework.samples.petclinic.service.VetService;
import org.springframework.samples.petclinic.service.clinicService.ApplicationTestConfig;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the ROLE_ADMIN > ROLE_OWNER_ADMIN / ROLE_ADMIN > ROLE_VET_ADMIN
 * role hierarchy declared in {@link BasicAuthenticationConfig#roleHierarchy()}.
 * See docs/adr/0007-admin-role-hierarchy.md.
 */
@SpringBootTest
@ContextConfiguration(classes = ApplicationTestConfig.class)
@WebAppConfiguration
class RoleHierarchyTests {

    @Autowired
    private VetRestControllerV1 vetRestControllerV1;

    @MockitoBean
    private VetService vetService;

    @MockitoBean
    private SpecialtyService specialtyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(vetRestControllerV1)
            .setControllerAdvice(new ExceptionControllerAdvice())
            .build();

        Vet vet = new Vet();
        vet.setId(1);
        vet.setFirstName("James");
        vet.setLastName("Carter");
        given(this.vetService.findAllVets()).willReturn(List.of(vet));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessVetAdminGatedEndpoint() throws Exception {
        this.mockMvc.perform(get("/api/vets").accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void ownerAdminIsStillDeniedTheVetAdminGatedEndpoint() throws Exception {
        // Confirms the hierarchy only runs ROLE_ADMIN downwards, not sideways between
        // the two resource-scoped roles.
        this.mockMvc.perform(get("/api/vets").accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("AuthorizationDeniedException"));
    }

}
