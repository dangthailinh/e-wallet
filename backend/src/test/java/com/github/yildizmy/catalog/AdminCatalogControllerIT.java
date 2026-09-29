package com.github.yildizmy.catalog;

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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCatalogController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.cookies.access-name=TEST_ACCESS",
        "app.security.cookies.refresh-name=TEST_REFRESH",
        "app.security.cookies.secure=true",
        "app.security.cookies.same-site=Lax"
})
class AdminCatalogControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogCommandService commandService;

    @MockitoBean
    private AuthEntryPointJwt authEntryPointJwt;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanListCategoriesAndProductsIncludingInactiveRecords() throws Exception {
        UUID categoryId = UUID.fromString("fa560a3d-660c-4e91-a7a9-5c5553fb4b87");
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        when(commandService.listCategories(0, 100))
                .thenReturn(new AdminCatalogPageResponse<>(
                        List.of(new AdminCategoryResponse(
                                categoryId, "AI", "ai", 1, CatalogStatus.INACTIVE, now, now, 2L
                        )),
                        new ProductListResponse.PageMetadata(0, 100, 1, 1, false, false)
                ));
        when(commandService.listProducts(0, 20))
                .thenReturn(new AdminCatalogPageResponse<>(
                        List.of(new AdminProductResponse(
                                UUID.fromString("ad3a7917-196d-4f6e-9235-03f36bca7caf"),
                                categoryId, "AI", "ai", "Gói API", "goi-api", "Mô tả", 50_000,
                                CatalogStatus.INACTIVE, null, now, now, 3L
                        )),
                        new ProductListResponse.PageMetadata(0, 20, 1, 1, false, false)
                ));

        mockMvc.perform(get("/api/v1/admin/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].status").value("INACTIVE"))
                .andExpect(jsonPath("$.page.number").value(0));

        mockMvc.perform(get("/api/v1/admin/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].status").value("INACTIVE"))
                .andExpect(jsonPath("$.page.number").value(0));
    }

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void customerCannotListAdminCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(commandService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanCreateValidatedProduct() throws Exception {
        UUID categoryId = UUID.fromString("fa560a3d-660c-4e91-a7a9-5c5553fb4b87");
        UUID productId = UUID.fromString("ad3a7917-196d-4f6e-9235-03f36bca7caf");
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        when(commandService.createProduct(any(), any())).thenReturn(new AdminProductResponse(
                productId,
                categoryId,
                "AI",
                "ai",
                "ChatGPT Plus",
                "chatgpt-plus",
                "Tài khoản giao tự động.",
                150_000,
                CatalogStatus.ACTIVE,
                "https://cdn.example.test/chatgpt-plus.png",
                now,
                now,
                0L
        ));

        mockMvc.perform(post("/api/v1/admin/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId":"fa560a3d-660c-4e91-a7a9-5c5553fb4b87",
                                  "name":"ChatGPT Plus",
                                  "slug":"chatgpt-plus",
                                  "description":"Tài khoản giao tự động.",
                                  "priceVnd":150000,
                                  "status":"ACTIVE",
                                  "imageUrl":"https://cdn.example.test/chatgpt-plus.png"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(productId.toString()))
                .andExpect(jsonPath("$.category.slug").value("ai"))
                .andExpect(jsonPath("$.priceVnd").value(150000))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(commandService).createProduct(any(ProductCommand.class), org.mockito.ArgumentMatchers.eq("admin"));
    }

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void customerCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/v1/admin/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId":"fa560a3d-660c-4e91-a7a9-5c5553fb4b87",
                                  "name":"ChatGPT Plus",
                                  "slug":"chatgpt-plus",
                                  "description":"Tài khoản giao tự động.",
                                  "priceVnd":150000,
                                  "status":"ACTIVE"
                                }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(commandService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void invalidProductIsRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/v1/admin/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId":"fa560a3d-660c-4e91-a7a9-5c5553fb4b87",
                                  "name":" ",
                                  "slug":"Not Valid",
                                  "description":" ",
                                  "priceVnd":0,
                                  "status":"ACTIVE",
                                  "imageUrl":"javascript:alert(1)"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.slug").exists())
                .andExpect(jsonPath("$.errors.priceVnd").exists())
                .andExpect(jsonPath("$.errors.imageUrl").exists());

        verifyNoInteractions(commandService);
    }
}
