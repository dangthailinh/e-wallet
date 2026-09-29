package com.github.yildizmy.order;

import com.github.yildizmy.config.SecurityConfig;
import com.github.yildizmy.exception.GlobalExceptionHandler;
import com.github.yildizmy.exception.NoSuchElementFoundException;
import com.github.yildizmy.security.AuthEntryPointJwt;
import com.github.yildizmy.security.JwtUtils;
import com.github.yildizmy.security.UserDetailsImpl;
import com.github.yildizmy.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.cookies.access-name=TEST_ACCESS",
        "app.security.cookies.refresh-name=TEST_REFRESH",
        "app.security.cookies.secure=true",
        "app.security.cookies.same-site=Lax"
})
class OrderControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckoutService checkout;

    @MockitoBean
    private OrderQueryService queryService;

    @MockitoBean
    private SecretRevealService revealService;

    @MockitoBean
    private AuthEntryPointJwt authEntryPointJwt;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @BeforeEach
    void configureUnauthorized() throws Exception {
        doAnswer(invocation -> {
            invocation.<jakarta.servlet.http.HttpServletResponse>getArgument(1).sendError(401);
            return null;
        }).when(authEntryPointJwt).commence(any(), any(), any());
    }

    @Test
    void checkoutUsesPrincipalAndIdempotencyHeaderWithoutReturningSecret() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(checkout.checkout(7L, productId, 50_000, "checkout-key-001")).thenReturn(new CheckoutResult(
                orderId, itemId, productId, "API package", 50_000,
                OrderStatus.COMPLETED, Instant.parse("2026-09-23T01:00:00Z"), 75_000, true
        ));

        mockMvc.perform(post("/api/v1/orders")
                        .with(user(buyer(7L)))
                        .with(csrf())
                        .header("Idempotency-Key", "checkout-key-001")
                        .contentType("application/json")
                        .content("{\"productId\":\"" + productId + "\",\"expectedPriceVnd\":50000,\"userId\":999}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.walletBalanceVnd").value(75_000))
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.inventoryItemId").doesNotExist());

        verify(checkout).checkout(7L, productId, 50_000, "checkout-key-001");
    }

    @Test
    void checkoutRequiresAuthenticationCsrfAndAValidIdempotencyKey() throws Exception {
        UUID productId = UUID.randomUUID();
        String body = "{\"productId\":\"" + productId + "\",\"expectedPriceVnd\":50000}";

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .header("Idempotency-Key", "checkout-key-001")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/orders")
                        .with(user(buyer(7L)))
                        .header("Idempotency-Key", "checkout-key-001")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/orders")
                        .with(user(buyer(7L)))
                        .with(csrf())
                        .header("Idempotency-Key", "bad key")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(checkout);
    }

    @Test
    void checkoutRejectsAChangedPriceWithAnActionableConflict() throws Exception {
        UUID productId = UUID.randomUUID();
        when(checkout.checkout(7L, productId, 50_000, "stale-price-001"))
                .thenThrow(new ProductPriceChangedException());

        mockMvc.perform(post("/api/v1/orders")
                        .with(user(buyer(7L)))
                        .with(csrf())
                        .header("Idempotency-Key", "stale-price-001")
                        .contentType("application/json")
                        .content("{\"productId\":\"" + productId + "\",\"expectedPriceVnd\":50000}"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("PRODUCT_PRICE_CHANGED"));
    }

    @Test
    void listAndDetailAreOwnerScopedPaginatedAndSafe() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-23T01:00:00Z");
        OrderSummaryResponse summary = new OrderSummaryResponse(
                orderId, OrderStatus.COMPLETED, 50_000, createdAt
        );
        OrderDetailResponse detail = new OrderDetailResponse(
                orderId, OrderStatus.COMPLETED, 50_000, createdAt,
                List.of(new OrderItemResponse(itemId, productId, "API package", 50_000))
        );
        when(queryService.list(7L, 0, 20)).thenReturn(new OrderListResponse(
                List.of(summary), 0, 20, 1, 1
        ));
        when(queryService.detail(7L, orderId)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/orders").with(user(buyer(7L))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.totalItems").value(1));
        mockMvc.perform(get("/api/v1/orders/{id}", orderId).with(user(buyer(7L))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].productName").value("API package"))
                .andExpect(jsonPath("$.items[0].secret").doesNotExist())
                .andExpect(jsonPath("$.items[0].inventoryItemId").doesNotExist());

        verify(queryService).list(7L, 0, 20);
        verify(queryService).detail(7L, orderId);
    }

    @Test
    void revealIsNoStoreAndCrossUserDenialDoesNotConfirmExistence() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        when(revealService.reveal(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(orderId), org.mockito.ArgumentMatchers.eq(itemId), anyString()))
                .thenReturn(new SecretRevealResponse(itemId, "purchased-secret"));
        when(revealService.reveal(org.mockito.ArgumentMatchers.eq(8L),
                org.mockito.ArgumentMatchers.eq(orderId), org.mockito.ArgumentMatchers.eq(itemId), anyString()))
                .thenThrow(new NoSuchElementFoundException("Order item not found"));

        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/reveal", orderId, itemId)
                        .with(user(buyer(7L)))
                        .with(csrf())
                        .header("X-Request-ID", "reveal-request-001"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.itemId").value(itemId.toString()))
                .andExpect(jsonPath("$.secret").value("purchased-secret"));
        mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/reveal", orderId, itemId)
                        .with(user(buyer(8L)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.detail").value("Order item not found"));

        verify(revealService).reveal(7L, orderId, itemId, "reveal-request-001");
    }

    @Test
    void corsPreflightAllowsTheIdempotencyHeaderFromTheStorefront() throws Exception {
        mockMvc.perform(options("/api/v1/orders")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Idempotency-Key,X-XSRF-TOKEN,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsStringIgnoringCase(
                        "Idempotency-Key"
                )));
    }

    private UserDetailsImpl buyer(long id) {
        return new UserDetailsImpl(
                id, "buyer", "not-used", "Buyer", "One",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
