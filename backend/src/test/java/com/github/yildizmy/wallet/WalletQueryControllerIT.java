package com.github.yildizmy.wallet;

import com.github.yildizmy.config.SecurityConfig;
import com.github.yildizmy.exception.GlobalExceptionHandler;
import com.github.yildizmy.security.AuthEntryPointJwt;
import com.github.yildizmy.security.JwtUtils;
import com.github.yildizmy.security.UserDetailsImpl;
import com.github.yildizmy.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletQueryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.cookies.access-name=TEST_ACCESS",
        "app.security.cookies.refresh-name=TEST_REFRESH",
        "app.security.cookies.secure=true",
        "app.security.cookies.same-site=Lax"
})
class WalletQueryControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletQueryService service;

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
    void currentWalletUsesPrincipalIdNotClientUserId() throws Exception {
        when(service.currentWallet(7L)).thenReturn(new WalletView(12L, 50_000, "VND"));

        mockMvc.perform(get("/api/v1/wallet?userId=999").with(user(buyer(7L))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.walletId").value(12))
                .andExpect(jsonPath("$.balanceVnd").value(50_000))
                .andExpect(jsonPath("$.currency").value("VND"));

        verify(service).currentWallet(7L);
    }

    @Test
    void entriesArePaginatedAndContainOnlySafeFields() throws Exception {
        WalletEntryView entry = new WalletEntryView(
                UUID.randomUUID(), WalletEntryDirection.CREDIT, WalletEntryType.DEPOSIT,
                5_000, 50_000, "MoMo deposit", Instant.parse("2026-09-21T01:00:00Z")
        );
        when(service.entries(7L, 1, 10)).thenReturn(new PageImpl<>(List.of(entry)));

        mockMvc.perform(get("/api/v1/wallet/entries?page=1&size=10").with(user(buyer(7L))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].amountVnd").value(5_000))
                .andExpect(jsonPath("$.items[0].businessLabel").value("MoMo deposit"))
                .andExpect(jsonPath("$.items[0].businessReference").doesNotExist())
                .andExpect(jsonPath("$.items[0].wallet").doesNotExist())
                .andExpect(jsonPath("$.totalItems").value(1));

        verify(service).entries(7L, 1, 10);
    }

    @Test
    void anonymousAndOversizedRequestsNeverQueryWalletData() throws Exception {
        mockMvc.perform(get("/api/v1/wallet"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/wallet/entries?size=10000").with(user(buyer(7L))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private UserDetailsImpl buyer(long id) {
        return new UserDetailsImpl(
                id, "buyer", "not-used", "Buyer", "One",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
