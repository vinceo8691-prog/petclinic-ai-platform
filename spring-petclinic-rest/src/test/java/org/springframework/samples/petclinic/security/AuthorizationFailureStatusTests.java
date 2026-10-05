package org.springframework.samples.petclinic.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.service.VetService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises a denied {@code @PreAuthorize} check through the real Spring Security
 * filter chain (unlike the other controller tests, which use
 * {@code MockMvcBuilders.standaloneSetup} and so never run {@code ExceptionTranslationFilter}),
 * to check whether the 500-instead-of-403 behavior seen there also happens in the
 * actually deployed app. See docs/architecture/authorization-denied-status-code.md.
 */
@SpringBootTest
@WebAppConfiguration
class AuthorizationFailureStatusTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private VetService vetService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.webApplicationContext)
            .apply(springSecurity())
            .build();
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void deniedPreAuthorizeCheckReturnsForbiddenThroughTheRealFilterChain() throws Exception {
        this.mockMvc.perform(get("/api/vets").accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isForbidden());
    }

}
