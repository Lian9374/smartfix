package com.smartfix.common.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GlobalExceptionHandlerTest.ErrorProbes.class)
@Import(GlobalExceptionHandlerTest.ErrorProbes.class)
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {
    @Autowired private MockMvc mvc;

    @ParameterizedTest
    @CsvSource({"missing,404", "invalid,400", "conflict,409", "database,409", "denied,403",
            "upload,413", "unexpected,500"})
    void mapsErrorsWithoutExposingTheirContents(String kind, int code) throws Exception {
        mvc.perform(get("/error-probe/" + kind)).andExpect(status().is(code))
                .andExpect(view().name("error"))
                .andExpect(content().string(not(containsString("sensitive-password"))))
                .andExpect(content().string(not(containsString("SELECT"))))
                .andExpect(content().string(not(containsString("C:\\private"))));
    }

    @Test
    void malformedIdsReturn400AndMissingPagesReturn404() throws Exception {
        mvc.perform(get("/error-probe/id/not-a-number")).andExpect(status().isBadRequest());
        mvc.perform(get("/missing-page")).andExpect(status().isNotFound());
    }

    @RestController
    static class ErrorProbes {
        @GetMapping("/error-probe/{kind}")
        String fail(@PathVariable String kind) {
            String detail = "sensitive-password SELECT C:\\private\\uploads";
            throw switch (kind) {
                case "missing" -> new ResourceNotFoundException(detail);
                case "invalid" -> new InputValidationException(detail);
                case "conflict" -> new BusinessConflictException(detail);
                case "database" -> new DataIntegrityViolationException(detail);
                case "denied" -> new AccessDeniedException(detail);
                case "upload" -> new MaxUploadSizeExceededException(1);
                default -> new IllegalStateException(detail);
            };
        }

        @GetMapping("/error-probe/id/{id}")
        String id(@PathVariable Long id) { return id.toString(); }
    }
}
