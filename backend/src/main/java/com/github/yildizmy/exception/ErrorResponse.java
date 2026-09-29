package com.github.yildizmy.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * RFC 9457-compatible API problem with stable machine-readable extensions.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        URI type,
        String title,
        int status,
        String detail,
        URI instance,
        String code,
        String requestId,
        Map<String, List<String>> errors
) {
}
