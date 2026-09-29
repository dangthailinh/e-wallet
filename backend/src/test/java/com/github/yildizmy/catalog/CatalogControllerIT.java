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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.security.cookies.access-name=TEST_ACCESS",
        "app.security.cookies.refresh-name=TEST_REFRESH",
        "app.security.cookies.secure=true",
        "app.security.cookies.same-site=Lax"
})
class CatalogControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogQueryService catalogQueryService;

    @MockitoBean
    private AuthEntryPointJwt authEntryPointJwt;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @Test
    void anonymousListMapsFiltersAndReturnsStablePagination() throws Exception {
        ProductView product = productView();
        CatalogQuery expectedQuery = new CatalogQuery(
                "GPT",
                "ai",
                CatalogAvailability.IN_STOCK,
                CatalogSort.PRICE_DESC,
                1,
                12
        );
        when(catalogQueryService.search(expectedQuery)).thenReturn(new PageImpl<>(
                List.of(product),
                PageRequest.of(1, 12),
                13
        ));

        mockMvc.perform(get("/api/v1/products")
                        .queryParam("q", "GPT")
                        .queryParam("category", "ai")
                        .queryParam("availability", "in-stock")
                        .queryParam("sort", "price-desc")
                        .queryParam("page", "1")
                        .queryParam("size", "12"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items[0].slug").value("chatgpt-plus"))
                .andExpect(jsonPath("$.items[0].priceVnd").value(150000))
                .andExpect(jsonPath("$.items[0].availableCount").value(4))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(12))
                .andExpect(jsonPath("$.page.totalItems").value(13))
                .andExpect(jsonPath("$.page.totalPages").value(2))
                .andExpect(jsonPath("$.page.hasNext").value(false))
                .andExpect(jsonPath("$.page.hasPrevious").value(true));

        verify(catalogQueryService).search(expectedQuery);
    }

    @Test
    void anonymousDetailReturnsAProductViewAndUnknownSlugIsNotFound() throws Exception {
        when(catalogQueryService.findBySlug("chatgpt-plus")).thenReturn(Optional.of(productView()));
        when(catalogQueryService.findBySlug("missing-product")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/products/chatgpt-plus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("chatgpt-plus"))
                .andExpect(jsonPath("$.category.slug").value("ai"))
                .andExpect(jsonPath("$.description").value("Digital delivery."))
                .andExpect(jsonPath("$.availableCount").value(4));

        mockMvc.perform(get("/api/v1/products/missing-product"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Product not found."));
    }

    @Test
    void invalidCatalogParametersReturnGenericStableProblems() throws Exception {
        mockMvc.perform(get("/api/v1/products").queryParam("sort", "drop-table"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"))
                .andExpect(jsonPath("$.detail").value("Catalog query parameters are invalid."));

        mockMvc.perform(get("/api/v1/products").queryParam("availability", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"));

        mockMvc.perform(get("/api/v1/products").queryParam("category", "Not Valid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"));

        mockMvc.perform(get("/api/v1/products").queryParam("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CATALOG_QUERY"));

        mockMvc.perform(get("/api/v1/products").queryParam("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("REQUEST_FAILED"));

        verifyNoInteractions(catalogQueryService);
    }

    private ProductView productView() {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        return new ProductView(
                UUID.fromString("ad3a7917-196d-4f6e-9235-03f36bca7caf"),
                UUID.fromString("fa560a3d-660c-4e91-a7a9-5c5553fb4b87"),
                "Artificial intelligence",
                "ai",
                "ChatGPT Plus",
                "chatgpt-plus",
                "Digital delivery.",
                150_000L,
                "https://cdn.example.test/chatgpt-plus.png",
                4,
                now,
                now
        );
    }
}
