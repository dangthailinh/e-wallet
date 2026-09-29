package com.github.yildizmy.exception;

import com.github.yildizmy.config.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void domainErrorUsesProblemJsonAndStableCode() throws Exception {
        mockMvc.perform(get("/missing").header(RequestIdFilter.REQUEST_ID_HEADER, "request-domain-123"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, "request-domain-123"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value("request-domain-123"))
                .andExpect(jsonPath("$", not(hasKey("stackTrace"))));
    }

    @Test
    void validationErrorHasStableCodeAndFieldErrors() throws Exception {
        mockMvc.perform(post("/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name[0]").exists());
    }

    @Test
    void unexpectedErrorDoesNotExposeInternalDetails() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("database-password"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("SELECT *"))));
    }

    @RestController
    static class FailingController {

        @GetMapping("/missing")
        String missing() {
            throw new NoSuchElementFoundException("Wallet was not found");
        }

        @PostMapping("/validation")
        String validation(@Valid @RequestBody ValidationInput input) {
            return input.name();
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("SELECT * failed with database-password");
        }
    }

    record ValidationInput(@NotBlank String name) {
    }
}
