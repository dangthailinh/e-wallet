package com.github.yildizmy.wallet;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class WalletProvisioningMigrationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void createsOnlyMissingZeroBalanceWalletsAndIsIdempotent() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        );
        Flyway.configure().dataSource(dataSource).target("10").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long existingWalletId = jdbc.queryForObject("select nextval('wallet_seq')", Long.class);
        jdbc.update(
                "insert into wallet (id, iban, name, balance, user_id, version) "
                        + "values (?, ?, 'Existing', 0, 2, 0)",
                existingWalletId,
                "VN" + String.format("%032d", existingWalletId)
        );

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(jdbc.queryForObject("select count(*) from wallet", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select count(*) from wallet where user_id = 2 and id = ?", Integer.class, existingWalletId
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from wallet where user_id in (1, 3) and balance = 0 "
                        + "and iban like 'EW%'", Integer.class
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from wallet_entries", Integer.class)).isZero();

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V11__provision_missing_wallets.sql")
            );
        }
        assertThat(jdbc.queryForObject("select count(*) from wallet", Integer.class)).isEqualTo(3);
    }
}
