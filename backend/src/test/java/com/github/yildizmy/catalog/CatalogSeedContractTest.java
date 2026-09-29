package com.github.yildizmy.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSeedContractTest {

    @Test
    void devProfileOwnsAnIdempotentSecretFreeCatalogSeed() throws Exception {
        ClassPathResource seed = new ClassPathResource("db/dev/R__seed_demo_catalog.sql");
        assertThat(seed.exists()).isTrue();

        String sql = seed.getContentAsString(StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("ON CONFLICT (slug) DO NOTHING")
                .doesNotContain("sk-")
                .doesNotContain("api_key")
                .doesNotContain("password=");

        String devConfig = new ClassPathResource("application-dev.yml")
                .getContentAsString(StandardCharsets.UTF_8);
        String defaultConfig = new ClassPathResource("application.yml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(devConfig).contains("classpath:db/dev");
        assertThat(defaultConfig).doesNotContain("classpath:db/dev");
    }
}
