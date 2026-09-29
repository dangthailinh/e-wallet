package com.github.yildizmy.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yildizmy.security.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(DepositControllerIT.ProviderConfiguration.class)
class DepositControllerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void authenticatedCreatePersistsBeforePreparingAndReturnsOnlySafeInstructions() throws Exception {
        Buyer buyer = buyer("create");

        mockMvc.perform(post("/api/v1/deposits?userId=999")
                        .with(user(principal(buyer.userId())))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"channel\":\"MOMO\",\"amountVnd\":50000}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.channel").value("MOMO"))
                .andExpect(jsonPath("$.providerCode").value("MOMO"))
                .andExpect(jsonPath("$.amountVnd").value(50_000))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.merchantReference").value(org.hamcrest.Matchers.matchesPattern(
                        "DEP_[A-F0-9]{32}"
                )))
                .andExpect(jsonPath("$.instructions.redirectUrl").value("https://pay.example.test/checkout"))
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.signature").doesNotExist())
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        Map<String, Object> stored = jdbc.queryForMap(
                "select user_id, wallet_id, amount_vnd, status from payment_deposits where user_id = ?",
                buyer.userId()
        );
        assertThat(stored).containsEntry("user_id", buyer.userId())
                .containsEntry("wallet_id", buyer.walletId())
                .containsEntry("amount_vnd", 50_000L)
                .containsEntry("status", "PENDING");
    }

    @Test
    void createRequiresAuthenticationCsrfAndValidAmountAndChannel() throws Exception {
        Buyer buyer = buyer("validation");
        String valid = "{\"channel\":\"MOMO\",\"amountVnd\":50000}";

        mockMvc.perform(post("/api/v1/deposits").with(csrf()).contentType("application/json").content(valid))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/deposits")
                        .with(user(principal(buyer.userId())))
                        .contentType("application/json")
                        .content(valid))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/deposits")
                        .with(user(principal(buyer.userId())))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"channel\":\"MOMO\",\"amountVnd\":0}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v1/deposits")
                        .with(user(principal(buyer.userId())))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"channel\":\"CARD\",\"amountVnd\":50000}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/deposits")
                        .with(user(principal(buyer.userId())))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"channel\":\"BANK\",\"amountVnd\":50000}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from payment_deposits", Long.class)).isZero();
    }

    @Test
    void statusIsOwnerOnlyAndClientCannotForcePaidTransition() throws Exception {
        Buyer owner = buyer("owner");
        Buyer stranger = buyer("stranger");
        MvcResult created = mockMvc.perform(post("/api/v1/deposits")
                        .with(user(principal(owner.userId())))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"channel\":\"MOMO\",\"amountVnd\":70000}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        UUID depositId = UUID.fromString(body.get("id").asText());

        mockMvc.perform(get("/api/v1/deposits/{id}", depositId).with(user(principal(owner.userId()))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(depositId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.instructions").doesNotExist());
        mockMvc.perform(get("/api/v1/deposits/{id}", depositId).with(user(principal(stranger.userId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/deposits/{id}", depositId)
                        .with(user(principal(owner.userId())))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"PAID\"}"))
                .andExpect(status().isMethodNotAllowed());

        assertThat(jdbc.queryForObject(
                "select status from payment_deposits where id = ?", String.class, depositId
        )).isEqualTo("PENDING");
    }

    private Buyer buyer(String label) {
        long userId = jdbc.queryForObject("select nextval('user_seq')", Long.class);
        long walletId = jdbc.queryForObject("select nextval('wallet_seq')", Long.class);
        jdbc.update(
                "insert into public.\"user\" (id, first_name, last_name, username, email, password) "
                        + "values (?, 'Deposit', 'Buyer', ?, ?, 'not-used')",
                userId, label + userId, label + userId + "@example.test"
        );
        jdbc.update(
                "insert into wallet (id, iban, name, balance, user_id, version) "
                        + "values (?, ?, 'Primary', 0, ?, 0)",
                walletId, "VN" + String.format("%032d", walletId), userId
        );
        return new Buyer(userId, walletId);
    }

    private UserDetailsImpl principal(long userId) {
        return new UserDetailsImpl(
                userId, "buyer-" + userId, "not-used", "Deposit", "Buyer",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    private record Buyer(long userId, long walletId) {
    }

    @TestConfiguration
    static class ProviderConfiguration {

        @Bean
        PaymentProvider momoTestProvider(JdbcTemplate jdbc) {
            return new PaymentProvider() {
                @Override
                public DepositChannel channel() {
                    return DepositChannel.MOMO;
                }

                @Override
                public String providerCode() {
                    return "MOMO";
                }

                @Override
                public PaymentInstructions prepare(PaymentPreparation payment) {
                    Long persisted = jdbc.queryForObject(
                            "select count(*) from payment_deposits where id = ? and merchant_reference = ?",
                            Long.class, payment.depositId(), payment.merchantReference()
                    );
                    if (persisted == null || persisted != 1L) {
                        throw new IllegalStateException("Deposit must be committed before provider preparation");
                    }
                    return PaymentInstructions.redirect(URI.create("https://pay.example.test/checkout"));
                }

                @Override
                public VerifiedNotification verify(byte[] rawBody, Map<String, List<String>> headers) {
                    throw new UnsupportedOperationException("Not needed by deposit creation tests");
                }
            };
        }
    }
}
