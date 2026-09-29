package com.github.yildizmy.wallet;

import com.github.yildizmy.controller.WalletController;
import com.github.yildizmy.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WalletMutationSecurityIT {

    private static final Set<Class<? extends Annotation>> MUTATION_MAPPINGS = Set.of(
            PostMapping.class,
            PutMapping.class,
            PatchMapping.class,
            DeleteMapping.class
    );

    @Test
    void publicWalletControllerHasNoClientControlledMutationRoute() {
        assertThat(Stream.of(WalletController.class.getDeclaredMethods())
                .filter(this::hasMutationMapping)
                .map(Method::getName)
                .toList()).isEmpty();
    }

    @Test
    void legacyServiceHasNoBalanceMutationBackdoor() {
        assertThat(Stream.of(WalletService.class.getDeclaredMethods())
                .map(Method::getName)
                .toList()).doesNotContain(
                "create",
                "update",
                "deleteById",
                "addFunds",
                "withdrawFunds",
                "transferFunds"
        );
    }

    private boolean hasMutationMapping(Method method) {
        return MUTATION_MAPPINGS.stream().anyMatch(method::isAnnotationPresent);
    }
}
