package com.github.yildizmy.security;

import com.github.yildizmy.config.MessageSourceConfig;
import com.github.yildizmy.config.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class AuthEntryPointJwtTest {

    @Test
    void returnsGenericUnauthorizedMessageWithoutEchoingParserDetails() throws Exception {
        AuthEntryPointJwt entryPoint = new AuthEntryPointJwt(new MessageSourceConfig());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.setServletPath("/api/v1/orders");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "auth-request-001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("JWT contains Bearer secret-token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("\"message\":\"Unauthorized\"")
                .doesNotContain("secret-token");
    }
}
