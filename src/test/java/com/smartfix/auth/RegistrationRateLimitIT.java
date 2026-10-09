package com.smartfix.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:registration-quota;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "smartfix.bootstrap-admin.enabled=false",
        "smartfix.registration.rate-limit.max-per-address=2"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RegistrationRateLimitIT {
    @Autowired MockMvc mvc;

    @Test void validationFailuresNewSessionsAndForwardedHeadersDoNotBypassQuota() throws Exception {
        mvc.perform(post("/register")).andExpect(status().isForbidden());
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/register").with(csrf()).header("X-Forwarded-For", "192.0.2." + i))
                    .andExpect(status().isBadRequest());
        }
        String page = mvc.perform(post("/register").with(csrf())
                        .header("X-Forwarded-For", "192.0.2.99")
                        .param("password", "SyntheticSecret9").param("confirmPassword", "SyntheticSecret9"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(view().name("register"))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Too many sign-up attempts").doesNotContain("SyntheticSecret9");
        mvc.perform(get("/register")).andExpect(status().isOk());
        mvc.perform(post("/register").with(csrf()).with(request -> {
            request.setRemoteAddr("192.0.2.20"); return request;
        })).andExpect(status().isBadRequest());
    }
}
