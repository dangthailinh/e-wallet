package com.github.yildizmy.inventory;

import com.github.yildizmy.config.SecurityConfig;
import com.github.yildizmy.exception.GlobalExceptionHandler;
import com.github.yildizmy.security.AuthEntryPointJwt;
import com.github.yildizmy.security.JwtUtils;
import com.github.yildizmy.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminInventoryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.cookies.access-name=TEST_ACCESS",
        "app.security.cookies.refresh-name=TEST_REFRESH",
        "app.security.cookies.secure=true",
        "app.security.cookies.same-site=Lax"
})
class AdminInventoryControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryImportService importService;

    @MockitoBean
    private InventoryAdminService adminService;

    @MockitoBean
    private AuthEntryPointJwt authEntryPointJwt;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminImportsBatchWithoutEchoingSecrets() throws Exception {
        UUID productId = UUID.fromString("ad3a7917-196d-4f6e-9235-03f36bca7caf");
        UUID itemId = UUID.fromString("ba3a7917-196d-4f6e-9235-03f36bca7caf");
        when(importService.importBatch(eq(productId), any(), eq("admin"))).thenReturn(new InventoryImportResponse(
                1,
                1,
                List.of(InventoryImportRowResult.imported(1, itemId))
        ));

        mockMvc.perform(post("/api/v1/admin/products/{productId}/inventory-items", productId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"secrets":["do-not-echo-this-secret"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requested").value(1))
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.results[0].inventoryItemId").value(itemId.toString()))
                .andExpect(content().string(not(containsString("do-not-echo-this-secret"))));

        verify(importService).importBatch(eq(productId), any(InventoryImportRequest.class), eq("admin"));
    }

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void customerCannotImportInventory() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/admin/products/{productId}/inventory-items",
                        UUID.randomUUID()
                ).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"secrets\":[\"value\"]}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(importService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void emptyBatchIsRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/admin/products/{productId}/inventory-items",
                        UUID.randomUUID()
                ).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"secrets\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(importService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminListContainsOnlyMaskedMetadataAndIsNotCacheable() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-18T01:00:00Z");
        when(adminService.list(productId)).thenReturn(new AdminInventoryListResponse(
                productId,
                Map.of(
                        InventoryStatus.AVAILABLE, 1L,
                        InventoryStatus.RESERVED, 0L,
                        InventoryStatus.SOLD, 0L,
                        InventoryStatus.DISABLED, 0L
                ),
                List.of(new AdminInventoryItemResponse(
                        itemId,
                        productId,
                        InventoryStatus.AVAILABLE,
                        "a1b2c3",
                        1,
                        now,
                        now
                ))
        ));

        mockMvc.perform(get("/api/v1/admin/products/{productId}/inventory-items", productId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].checksumSuffix").value("a1b2c3"))
                .andExpect(jsonPath("$.items[0].ciphertext").doesNotExist())
                .andExpect(jsonPath("$.items[0].nonce").doesNotExist())
                .andExpect(jsonPath("$.items[0].authTag").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanDisableAvailableItem() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-18T01:00:00Z");
        when(adminService.changeStatus(itemId, InventoryStatus.DISABLED, "admin"))
                .thenReturn(new AdminInventoryItemResponse(
                        itemId,
                        productId,
                        InventoryStatus.DISABLED,
                        "a1b2c3",
                        1,
                        now,
                        now
                ));

        mockMvc.perform(patch("/api/v1/admin/inventory-items/{itemId}/status", itemId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        verify(adminService).changeStatus(itemId, InventoryStatus.DISABLED, "admin");
    }
}
