package com.github.yildizmy.wallet;

import com.github.yildizmy.config.SecurityConfig;
import com.github.yildizmy.controller.TransactionController;
import com.github.yildizmy.controller.WalletController;
import com.github.yildizmy.exception.GlobalExceptionHandler;
import com.github.yildizmy.security.AuthEntryPointJwt;
import com.github.yildizmy.security.JwtUtils;
import com.github.yildizmy.security.UserDetailsServiceImpl;
import com.github.yildizmy.service.TransactionService;
import com.github.yildizmy.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({WalletController.class, TransactionController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.cookies.access-name=TEST_ACCESS",
        "app.security.cookies.refresh-name=TEST_REFRESH",
        "app.security.cookies.secure=true",
        "app.security.cookies.same-site=Lax"
})
class LegacyWalletReadSecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private AuthEntryPointJwt authEntryPointJwt;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void customerCannotReadArbitraryWalletOrLegacyTransaction() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/42"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/wallets/users/7"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/transactions/42"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/transactions/users/7"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(walletService, transactionService);
    }
}
