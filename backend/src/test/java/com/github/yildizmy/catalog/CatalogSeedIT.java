package com.github.yildizmy.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CatalogSeedIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void seedsRealisticMetadataAndCanRunTwiceWithoutDuplicates() throws Exception {
        assertCounts(5, 18);

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/dev/R__seed_demo_catalog.sql")
            );
        }

        assertCounts(5, 18);
        Long suspiciousValues = jdbcTemplate.queryForObject(
                "select count(*) from products where lower(description) like '%password=%' "
                        + "or lower(description) like '%api_key%' or lower(description) like '%sk-%'",
                Long.class
        );
        assertThat(suspiciousValues).isZero();
    }

    private void assertCounts(int expectedCategories, int expectedProducts) {
        Long categories = jdbcTemplate.queryForObject("select count(*) from categories", Long.class);
        Long products = jdbcTemplate.queryForObject("select count(*) from products", Long.class);
        assertThat(categories).isEqualTo(expectedCategories);
        assertThat(products).isEqualTo(expectedProducts);
    }
}
