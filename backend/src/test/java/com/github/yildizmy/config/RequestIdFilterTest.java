package com.github.yildizmy.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void echoesValidRequestIdInResponseAndMdc() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "client-request-123");
        var response = new MockHttpServletResponse();
        var requestIdSeenInChain = new AtomicReference<String>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                requestIdSeenInChain.set(MDC.get(RequestIdFilter.MDC_KEY)));

        assertEquals("client-request-123", response.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
        assertEquals("client-request-123", requestIdSeenInChain.get());
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void replacesUnsafeRequestIdWithGeneratedUuid() throws ServletException, IOException {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "short");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
        });

        String generated = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertNotEquals("short", generated);
        assertTrue(generated.matches("[0-9a-f-]{36}"));
    }
}
