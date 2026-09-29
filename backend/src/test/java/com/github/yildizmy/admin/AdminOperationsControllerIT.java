package com.github.yildizmy.admin;

import com.github.yildizmy.config.SecurityConfig;
import com.github.yildizmy.exception.GlobalExceptionHandler;
import com.github.yildizmy.security.AuthEntryPointJwt;
import com.github.yildizmy.security.JwtUtils;
import com.github.yildizmy.security.UserDetailsServiceImpl;
import com.github.yildizmy.order.OrderStatus;
import com.github.yildizmy.payment.DepositChannel;
import com.github.yildizmy.payment.DepositStatus;
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

@WebMvcTest(AdminOperationsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.cookies.access-name=TEST_ACCESS",
        "app.security.cookies.refresh-name=TEST_REFRESH",
        "app.security.cookies.secure=true",
        "app.security.cookies.same-site=Lax"
})
class AdminOperationsControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminOperationsQueryService queryService;

    @MockitoBean
    private AuthEntryPointJwt authEntryPointJwt;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanListOrdersWithSafeFieldsAndFilters() throws Exception {
        UUID orderId = UUID.fromString("7f3e5c01-4b31-4c99-a5b7-60f9e3c0e001");
        Instant now = Instant.parse("2026-09-28T03:00:00Z");
        when(queryService.listOrders(0, 20, "COMPLETED", 42L)).thenReturn(new AdminPageResponse<>(
                List.of(new AdminOrderResponse(orderId, 42L, OrderStatus.COMPLETED, 150_000, now, now)),
                new AdminPageResponse.PageMetadata(0, 20, 1, 1, false, false)
        ));

        mockMvc.perform(get("/api/v1/admin/orders")
                        .param("page", "0")
                        .param("size", "20")
                        .param("status", "COMPLETED")
                        .param("userId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.items[0].userId").value(42))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].totalVnd").value(150000))
                .andExpect(jsonPath("$.items[0].createdAt").value(now.toString()))
                .andExpect(jsonPath("$.items[0].productName").doesNotExist())
                .andExpect(jsonPath("$.items[0].merchantReference").doesNotExist())
                .andExpect(jsonPath("$.page.number").value(0));

        verify(queryService).listOrders(0, 20, "COMPLETED", 42L);
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanListDepositsWithReviewEvidenceWithoutPaymentSecrets() throws Exception {
        UUID depositId = UUID.fromString("7f3e5c01-4b31-4c99-a5b7-60f9e3c0e002");
        Instant now = Instant.parse("2026-09-28T03:00:00Z");
        when(queryService.listDeposits(0, 20, "REVIEW", "MOMO", 42L)).thenReturn(new AdminPageResponse<>(
                List.of(new AdminDepositResponse(
                        depositId, 42L, DepositChannel.MOMO, "MOMO", 200_000, "VND",
                        DepositStatus.REVIEW, now.plusSeconds(900), null, now, now, true
                )),
                new AdminPageResponse.PageMetadata(0, 20, 1, 1, false, false)
        ));

        mockMvc.perform(get("/api/v1/admin/deposits")
                        .param("page", "0")
                        .param("size", "20")
                        .param("status", "REVIEW")
                        .param("channel", "MOMO")
                        .param("userId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(depositId.toString()))
                .andExpect(jsonPath("$.items[0].userId").value(42))
                .andExpect(jsonPath("$.items[0].providerCode").value("MOMO"))
                .andExpect(jsonPath("$.items[0].status").value("REVIEW"))
                .andExpect(jsonPath("$.items[0].needsReconciliationReview").value(true))
                .andExpect(jsonPath("$.items[0].merchantReference").doesNotExist())
                .andExpect(jsonPath("$.items[0].providerTransactionId").doesNotExist())
                .andExpect(jsonPath("$.items[0].instructions").doesNotExist())
                .andExpect(jsonPath("$.page.totalItems").value(1));

        verify(queryService).listDeposits(0, 20, "REVIEW", "MOMO", 42L);
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "admin", roles = "ADMIN")
    void invalidAdminQueryReturnsStructuredBadRequest() throws Exception {
        when(queryService.listOrders(0, 20, "INVALID", null))
                .thenThrow(new InvalidAdminOperationsQueryException());

        mockMvc.perform(get("/api/v1/admin/orders").param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADMIN_OPERATIONS_QUERY"));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "customer", roles = "USER")
    void customerCannotListAdminOperations() throws Exception {
        mockMvc.perform(get("/api/v1/admin/orders"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/deposits"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(queryService);
    }
}
