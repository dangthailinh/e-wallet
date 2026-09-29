package com.github.yildizmy.catalog;

import com.github.yildizmy.exception.ErrorResponse;
import com.github.yildizmy.exception.InvalidCatalogQueryException;
import com.github.yildizmy.exception.NoSuchElementFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Catalog", description = "Public digital product catalog")
public class CatalogController {

    private final CatalogQueryService catalogQueryService;

    public CatalogController(CatalogQueryService catalogQueryService) {
        this.catalogQueryService = catalogQueryService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "listProducts", summary = "Search and filter public products")
    @ApiResponse(
            responseCode = "200",
            description = "Product page",
            content = @Content(schema = @Schema(implementation = ProductListResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid query parameter",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    public ProductListResponse list(
            @Parameter(example = "chatgpt") @RequestParam(required = false) String q,
            @Parameter(example = "ai") @RequestParam(required = false) String category,
            @Parameter(example = "any", schema = @Schema(allowableValues = {"any", "in-stock", "out-of-stock"}))
            @RequestParam(defaultValue = "any") String availability,
            @Parameter(example = "relevance", schema = @Schema(
                    allowableValues = {"relevance", "newest", "price-asc", "price-desc", "name-asc"}
            ))
            @RequestParam(defaultValue = "relevance") String sort,
            @Parameter(example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        CatalogQuery query = parseQuery(q, category, availability, sort, page, size);
        return ProductListResponse.from(catalogQueryService.search(query));
    }

    @GetMapping(value = "/{slug}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getProductBySlug", summary = "Get one public product by slug")
    @ApiResponse(
            responseCode = "200",
            description = "Product details",
            content = @Content(schema = @Schema(implementation = ProductDetailResponse.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "Product is missing or not public",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    public ProductDetailResponse detail(
            @Parameter(example = "chatgpt-plus") @PathVariable String slug
    ) {
        return catalogQueryService.findBySlug(slug)
                .map(ProductDetailResponse::from)
                .orElseThrow(() -> new NoSuchElementFoundException("Product not found."));
    }

    private CatalogQuery parseQuery(
            String q,
            String category,
            String availability,
            String sort,
            int page,
            int size
    ) {
        try {
            return new CatalogQuery(
                    q,
                    category,
                    CatalogAvailability.fromQueryValue(availability),
                    CatalogSort.fromQueryValue(sort),
                    page,
                    size
            );
        } catch (IllegalArgumentException exception) {
            throw new InvalidCatalogQueryException();
        }
    }
}
