package com.github.yildizmy.payment.momo;

import com.github.yildizmy.payment.PaymentInstructions;
import com.github.yildizmy.payment.PaymentPreparation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MomoClientTest {

    private static final URI CREATE_URL = URI.create("https://test-payment.momo.vn/v2/gateway/api/create");
    private static final String SECRET = "fixture-secret-key-32-characters";
    private static final UUID DEPOSIT_ID = UUID.fromString("1e89fbe4-c243-40f8-8458-125afcd5bde4");

    private MockRestServiceServer server;
    private MomoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MomoClient(builder.build(), properties());
    }

    @Test
    void createsSignedPaymentAndReturnsOnlyValidatedHttpsPayUrl() {
        String payUrl = "https://test-payment.momo.vn/v2/gateway/pay?t=safe-token";
        MomoCreateResponse response = new MomoCreateResponse(
                "FIXTURE_PARTNER", "DEP_ABC12345", DEPOSIT_ID.toString().replace("-", ""),
                100_000, 1_727_000_000_000L, "Successful.", 0, payUrl, null
        );
        String signature = MomoSignature.signCreateResponse("fixture-access", SECRET, response);

        server.expect(once(), requestTo(CREATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "partnerCode":"FIXTURE_PARTNER",
                          "requestType":"captureWallet",
                          "orderId":"DEP_ABC12345",
                          "amount":100000,
                          "requestId":"1e89fbe4c24340f88458125afcd5bde4",
                          "extraData":"",
                          "lang":"vi"
                        }
                        """, false))
                .andRespond(withSuccess("""
                        {
                          "partnerCode":"FIXTURE_PARTNER",
                          "orderId":"DEP_ABC12345",
                          "requestId":"1e89fbe4c24340f88458125afcd5bde4",
                          "amount":100000,
                          "responseTime":1727000000000,
                          "message":"Successful.",
                          "resultCode":0,
                          "payUrl":"%s",
                          "signature":"%s"
                        }
                        """.formatted(payUrl, signature), MediaType.APPLICATION_JSON));

        PaymentInstructions instructions = client.create(new PaymentPreparation(
                DEPOSIT_ID, "DEP_ABC12345", 100_000, "VND", Instant.now().plusSeconds(900)
        ));

        assertThat(instructions.redirectUrl()).hasToString(payUrl);
        assertThat(instructions.qrPayload()).isNull();
        server.verify();
    }

    @Test
    void rejectsProviderErrorsWithoutLeakingTheirResponseBody() {
        server.expect(requestTo(CREATE_URL)).andRespond(withRawStatus(502)
                .body("secret upstream diagnostic containing credentials")
                .contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.create(new PaymentPreparation(
                DEPOSIT_ID, "DEP_ABC12345", 100_000, "VND", Instant.now().plusSeconds(900)
        )))
                .isInstanceOf(MomoProviderException.class)
                .hasMessage("MoMo create-payment request failed")
                .hasMessageNotContaining("credentials");
    }

    @Test
    void queriesTransactionStatusOnlyAtTheDerivedOfficialGatewayUrl() {
        URI queryUrl = URI.create("https://test-payment.momo.vn/v2/gateway/api/query");
        MomoQueryResponse response = new MomoQueryResponse(
                "FIXTURE_PARTNER", "DEP_ABC12345", DEPOSIT_ID.toString().replace("-", ""),
                100_000, 1_727_000_000_000L, "Successful.", 0, 99887766L, "", null
        );
        String signature = MomoSignature.signQueryResponse("fixture-access", SECRET, response);
        server.expect(once(), requestTo(queryUrl))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "partnerCode":"FIXTURE_PARTNER",
                          "orderId":"DEP_ABC12345",
                          "requestId":"1e89fbe4c24340f88458125afcd5bde4",
                          "requestType":"queryTransaction"
                        }
                        """, false))
                .andRespond(withSuccess("""
                        {
                          "partnerCode":"FIXTURE_PARTNER",
                          "orderId":"DEP_ABC12345",
                          "requestId":"1e89fbe4c24340f88458125afcd5bde4",
                          "amount":100000,
                          "responseTime":1727000000000,
                          "message":"Successful.",
                          "resultCode":0,
                          "transId":99887766,
                          "extraData":"",
                          "signature":"%s"
                        }
                        """.formatted(signature), MediaType.APPLICATION_JSON));

        Optional<MomoQueryResponse> queried = client.query(new PaymentPreparation(
                DEPOSIT_ID, "DEP_ABC12345", 100_000, "VND", Instant.now().plusSeconds(900)
        ));

        assertThat(queried).isPresent();
        assertThat(queried.orElseThrow())
                .usingRecursiveComparison()
                .ignoringFields("signature")
                .isEqualTo(response);
        server.verify();
    }

    @Test
    void validatesCredentialsUrlsTimeoutsAndProviderAmountBounds() {
        assertThatThrownBy(() -> new MomoProperties(
                URI.create("http://test-payment.momo.vn/v2/gateway/api/create"),
                "FIXTURE_PARTNER", "fixture-access", SECRET,
                URI.create("https://shop.example.com/top-up"),
                URI.create("https://shop.example.com/api/v1/webhooks/momo"),
                Duration.ofSeconds(5), Duration.ofSeconds(30)
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS");

        assertThatThrownBy(() -> new MomoProperties(
                CREATE_URL, "FIXTURE_PARTNER", "fixture-access", SECRET,
                URI.create("https://shop.example.com/top-up"),
                URI.create("https://shop.example.com/api/v1/webhooks/momo"),
                Duration.ofSeconds(5), Duration.ofSeconds(29)
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("30 seconds");

        assertThatThrownBy(() -> client.create(new PaymentPreparation(
                DEPOSIT_ID, "DEP_ABC12345", 999, "VND", Instant.now().plusSeconds(900)
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1,000");

        assertThat(properties().toString())
                .doesNotContain("fixture-access")
                .doesNotContain(SECRET)
                .contains("REDACTED");
    }

    private static MomoProperties properties() {
        return new MomoProperties(
                CREATE_URL,
                "FIXTURE_PARTNER",
                "fixture-access",
                SECRET,
                URI.create("https://shop.example.com/top-up"),
                URI.create("https://shop.example.com/api/v1/webhooks/momo"),
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)
        );
    }
}
