package com.github.yildizmy.audit;

import com.github.yildizmy.admin.AdminPageResponse;
import com.github.yildizmy.config.SecurityConfig;
import com.github.yildizmy.exception.GlobalExceptionHandler;
import com.github.yildizmy.security.AuthEntryPointJwt;
import com.github.yildizmy.security.JwtUtils;
import com.github.yildizmy.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.cookies.access-name=TEST_ACCESS",
        "app.security.cookies.refresh-name=TEST_REFRESH",
        "app.security.cookies.secure=true",
        "app.security.cookies.same-site=Lax"
})
class AuditControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private AuthEntryPointJwt authEntryPointJwt;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "admin", roles = "ADMIN")
    void adminReceivesAllowListedAuditFields() throws Exception {
        UUID resourceId = UUID.fromString("7f3e5c01-4b31-4c99-a5b7-60f9e3c0e001");
        Instant now = Instant.parse("2026-09-28T03:00:00Z");
        when(auditService.list(0, 20, 42L, "SECRET_REVEALED", "ORDER_ITEM", resourceId))
                .thenReturn(new AdminPageResponse<>(
                        List.of(new AuditLogResponse(
                                UUID.randomUUID(), 42L, "SECRET_REVEALED", "ORDER_ITEM",
                                resourceId, null, "request-audit-001", now
                        )),
                        new AdminPageResponse.PageMetadata(0, 20, 1, 1, false, false)
                ));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("page", "0")
                        .param("size", "20")
                        .param("actorId", "42")
                        .param("action", "SECRET_REVEALED")
                        .param("resourceType", "ORDER_ITEM")
                        .param("resourceId", resourceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actorUserId").value(42))
                .andExpect(jsonPath("$.items[0].action").value("SECRET_REVEALED"))
                .andExpect(jsonPath("$.items[0].requestId").value("request-audit-001"))
                .andExpect(jsonPath("$.items[0].metadata").doesNotExist())
                .andExpect(jsonPath("$.items[0].secret").doesNotExist());

        verify(auditService).list(0, 20, 42L, "SECRET_REVEALED", "ORDER_ITEM", resourceId);
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "customer", roles = "USER")
    void customerCannotReadAuditLogs() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auditService);
    }
}
