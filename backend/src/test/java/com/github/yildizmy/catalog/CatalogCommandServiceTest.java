package com.github.yildizmy.catalog;

import com.github.yildizmy.exception.ElementAlreadyExistsException;
import com.github.yildizmy.exception.NoSuchElementFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogCommandServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    private CatalogCommandService service;

    @BeforeEach
    void setUp() {
        service = new CatalogCommandService(categoryRepository, productRepository);
    }

    @Test
    void createsProductFromValidatedCommandWithoutReturningJpaEntity() {
        UUID categoryId = UUID.randomUUID();
        Category category = new Category("AI", "ai", 1, CatalogStatus.ACTIVE);
        ProductCommand command = new ProductCommand(
                categoryId,
                "ChatGPT Plus",
                "chatgpt-plus",
                "Tài khoản giao tự động.",
                150_000,
                CatalogStatus.ACTIVE,
                "https://cdn.example.test/chatgpt-plus.png"
        );
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminProductResponse response = service.createProduct(command, "admin");

        assertThat(response.name()).isEqualTo("ChatGPT Plus");
        assertThat(response.category().slug()).isEqualTo("ai");
        assertThat(response.priceVnd()).isEqualTo(150_000);
        verify(productRepository).saveAndFlush(any(Product.class));
    }

    @Test
    void rejectsDuplicateProductSlugBeforeLookingUpCategory() {
        ProductCommand command = new ProductCommand(
                UUID.randomUUID(),
                "ChatGPT Plus",
                "chatgpt-plus",
                "Tài khoản giao tự động.",
                150_000,
                CatalogStatus.ACTIVE,
                null
        );
        when(productRepository.existsBySlugIgnoreCase("chatgpt-plus")).thenReturn(true);

        assertThatThrownBy(() -> service.createProduct(command, "admin"))
                .isInstanceOf(ElementAlreadyExistsException.class)
                .hasMessage("Catalog slug is already in use.");

        verify(categoryRepository, never()).findById(any());
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void productStatusChangeDoesNotCreateOrReplaceProduct() {
        UUID productId = UUID.randomUUID();
        Category category = new Category("AI", "ai", 1, CatalogStatus.ACTIVE);
        Product product = new Product(
                category,
                "ChatGPT Plus",
                "chatgpt-plus",
                "Tài khoản giao tự động.",
                150_000,
                CatalogStatus.ACTIVE,
                null
        );
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        AdminProductResponse response = service.changeProductStatus(
                productId,
                CatalogStatus.INACTIVE,
                "admin"
        );

        assertThat(response.status()).isEqualTo(CatalogStatus.INACTIVE);
        verify(productRepository).flush();
        verify(productRepository, never()).save(any());
    }

    @Test
    void unknownCategoryFailsWithStableDomainError() {
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeCategoryStatus(categoryId, CatalogStatus.INACTIVE, "admin"))
                .isInstanceOf(NoSuchElementFoundException.class)
                .hasMessage("Category not found.");
    }
}
