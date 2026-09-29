package com.github.yildizmy.catalog;

import com.github.yildizmy.exception.ElementAlreadyExistsException;
import com.github.yildizmy.exception.InvalidCatalogQueryException;
import com.github.yildizmy.exception.NoSuchElementFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class CatalogCommandService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CatalogCommandService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public AdminCatalogPageResponse<AdminCategoryResponse> listCategories(int page, int size) {
        PageRequest pageable = pageable(page, size, Sort.by("displayOrder").ascending().and(Sort.by("name")));
        return AdminCatalogPageResponse.from(categoryRepository.findAll(pageable).map(AdminCategoryResponse::from));
    }

    @Transactional(readOnly = true)
    public AdminCatalogPageResponse<AdminProductResponse> listProducts(int page, int size) {
        PageRequest pageable = pageable(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        return AdminCatalogPageResponse.from(productRepository.findAll(pageable).map(AdminProductResponse::from));
    }

    public AdminCategoryResponse createCategory(CategoryCommand command, String actor) {
        requireActor(actor);
        if (categoryRepository.existsBySlugIgnoreCase(command.slug())) {
            throw new ElementAlreadyExistsException("Catalog slug is already in use.");
        }
        Category category = categoryRepository.saveAndFlush(new Category(
                command.name(), command.slug(), command.displayOrder(), CatalogStatus.ACTIVE
        ));
        audit("CATEGORY_CREATED", actor, "category", category.getId());
        return AdminCategoryResponse.from(category);
    }

    public AdminCategoryResponse updateCategory(UUID id, CategoryCommand command, String actor) {
        requireActor(actor);
        Category category = requireCategory(id);
        if (categoryRepository.existsBySlugIgnoreCaseAndIdNot(command.slug(), id)) {
            throw new ElementAlreadyExistsException("Catalog slug is already in use.");
        }
        category.updateDetails(command.name(), command.slug(), command.displayOrder());
        categoryRepository.flush();
        audit("CATEGORY_UPDATED", actor, "category", id);
        return AdminCategoryResponse.from(category);
    }

    public AdminCategoryResponse changeCategoryStatus(UUID id, CatalogStatus status, String actor) {
        requireActor(actor);
        Category category = requireCategory(id);
        category.changeStatus(status);
        categoryRepository.flush();
        audit("CATEGORY_STATUS_CHANGED", actor, "category", id);
        return AdminCategoryResponse.from(category);
    }

    public AdminProductResponse createProduct(ProductCommand command, String actor) {
        requireActor(actor);
        if (productRepository.existsBySlugIgnoreCase(command.slug())) {
            throw new ElementAlreadyExistsException("Catalog slug is already in use.");
        }
        Category category = requireCategory(command.categoryId());
        Product product = productRepository.saveAndFlush(new Product(
                category,
                command.name(),
                command.slug(),
                command.description(),
                command.priceVnd(),
                command.status(),
                command.imageUrl()
        ));
        audit("PRODUCT_CREATED", actor, "product", product.getId());
        return AdminProductResponse.from(product);
    }

    public AdminProductResponse updateProduct(UUID id, ProductCommand command, String actor) {
        requireActor(actor);
        Product product = requireProduct(id);
        if (productRepository.existsBySlugIgnoreCaseAndIdNot(command.slug(), id)) {
            throw new ElementAlreadyExistsException("Catalog slug is already in use.");
        }
        Category category = requireCategory(command.categoryId());
        product.updateDetails(
                category,
                command.name(),
                command.slug(),
                command.description(),
                command.priceVnd(),
                command.imageUrl()
        );
        product.changeStatus(command.status());
        productRepository.flush();
        audit("PRODUCT_UPDATED", actor, "product", id);
        return AdminProductResponse.from(product);
    }

    public AdminProductResponse changeProductStatus(UUID id, CatalogStatus status, String actor) {
        requireActor(actor);
        Product product = requireProduct(id);
        product.changeStatus(status);
        productRepository.flush();
        audit("PRODUCT_STATUS_CHANGED", actor, "product", id);
        return AdminProductResponse.from(product);
    }

    private Category requireCategory(UUID id) {
        return categoryRepository.findById(Objects.requireNonNull(id, "id is required"))
                .orElseThrow(() -> new NoSuchElementFoundException("Category not found."));
    }

    private Product requireProduct(UUID id) {
        return productRepository.findById(Objects.requireNonNull(id, "id is required"))
                .orElseThrow(() -> new NoSuchElementFoundException("Product not found."));
    }

    private static void requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor is required");
        }
    }

    private static PageRequest pageable(int page, int size, Sort sort) {
        if (page < 0 || page > 10_000 || size < 1 || size > 100) {
            throw new InvalidCatalogQueryException();
        }
        return PageRequest.of(page, size, sort);
    }

    private void audit(String action, String actor, String resourceType, UUID resourceId) {
        String safeActor = actor.replace('\r', '_').replace('\n', '_').replace('\t', '_');
        log.info(
                "securityAudit action={} actor={} resourceType={} resourceId={}",
                action,
                safeActor,
                resourceType,
                resourceId
        );
    }
}
