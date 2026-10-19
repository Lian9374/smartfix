package com.smartfix.cloud;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = "smartfix.cloud.internal-token=worker-test-credential-at-least-32-characters")
@ContextConfiguration(classes = {DistributedSecurity.class, InternalDeliveryController.class})
@Import(DistributedSecurityTest.OtherInternalEndpoints.class)
@ActiveProfiles("distributed")
class DistributedSecurityTest {
    private static final String TOKEN = "Bearer worker-test-credential-at-least-32-characters";
    private static final String CALLBACK = "/internal/deliveries/7/complete";
    private static final String BODY = "{\"dedupKey\":\"event:7\",\"outcome\":\"DELIVERED\"}";

    @Autowired private MockMvc mvc;
    @MockitoBean private DeliveryCompletionService service;

    @Test void workerCanCompleteWithoutBrowserSessionOrCsrfToken() throws Exception {
        mvc.perform(post(CALLBACK).header("Authorization", TOKEN)
                        .contentType("application/json").content(BODY))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Set-Cookie"));
        verify(service).complete(7, "event:7", "DELIVERED");
    }

    @Test void wrongBearerCannotCompleteDelivery() throws Exception {
        mvc.perform(post(CALLBACK).header("Authorization", "Bearer wrong")
                        .contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void browserPostWithoutBearerIsRejected() throws Exception {
        mvc.perform(post(CALLBACK).contentType("application/json").content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void browserLoginCannotAuthorizeWorkerCallback() throws Exception {
        var session = new MockHttpSession();
        var admin = UsernamePasswordAuthenticationToken.authenticated("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR")));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(admin));
        mvc.perform(post(CALLBACK).session(session).with(csrf())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void workerCredentialCannotAuthorizeOtherInternalWrites() throws Exception {
        mvc.perform(post("/internal/unrelated").header("Authorization", TOKEN).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test void workerCredentialCannotAuthorizeOtherInternalReads() throws Exception {
        mvc.perform(get("/internal/unrelated").header("Authorization", TOKEN))
                .andExpect(status().isForbidden());
    }

    @RestController
    static class OtherInternalEndpoints {
        @GetMapping("/internal/unrelated") String read() { return "unexpected access"; }
        @PostMapping("/internal/unrelated") String write() { return "unexpected access"; }
    }
}
