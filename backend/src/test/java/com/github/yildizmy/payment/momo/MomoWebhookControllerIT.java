package com.github.yildizmy.payment.momo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yildizmy.config.BusinessMetrics;
import com.github.yildizmy.payment.VerifiedNotification;
import com.github.yildizmy.payment.WebhookProcessor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MomoWebhookControllerIT {

    private static final String VALID_BODY = """
            {
              "partnerCode":"FIXTURE_PARTNER",
              "orderId":"DEP_ABC12345",
              "requestId":"1e89fbe4c24340f88458125afcd5bde4",
              "amount":100000,
              "orderInfo":"Nap tien vi DEP_ABC12345",
              "orderType":"momo_wallet",
              "transId":4088878653,
              "resultCode":0,
              "message":"Successful.",
              "payType":"qr",
              "responseTime":1727000000000,
              "extraData":"",
              "signature":"1e8b67c3d36db1fe0e603f381f7940d7956493a4acc9ad1593730692e9557a9e"
            }
            """;

    private WebhookProcessor processor;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        processor = mock(WebhookProcessor.class);
        MomoPaymentProvider provider = new MomoPaymentProvider(
                mock(MomoClient.class), properties(), new ObjectMapper()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new MomoWebhookController(
                provider,
                processor,
                new BusinessMetrics(new SimpleMeterRegistry())
        )).build();
    }

    @Test
    void verifiesRawIpnAndReturnsNoContentAfterDispatchingToIdempotentProcessor() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/momo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNoContent());

        ArgumentCaptor<VerifiedNotification> notification = ArgumentCaptor.forClass(VerifiedNotification.class);
        ArgumentCaptor<byte[]> hash = ArgumentCaptor.forClass(byte[].class);
        verify(processor).process(notification.capture(), hash.capture());
        assertThat(notification.getValue()).satisfies(value -> {
            assertThat(value.providerCode()).isEqualTo("MOMO");
            assertThat(value.merchantReference()).isEqualTo("DEP_ABC12345");
            assertThat(value.amountVnd()).isEqualTo(100_000);
            assertThat(value.providerTransactionId()).isEqualTo("4088878653");
            assertThat(value.outcome()).isEqualTo(VerifiedNotification.Outcome.SUCCEEDED);
        });
        assertThat(hash.getValue()).hasSize(32);
    }

    @Test
    void rejectsForgedOrMismatchedNotificationsBeforeWalletProcessing() throws Exception {
        byte[] forged = VALID_BODY.replace("100000", "100001").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/webhooks/momo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forged))
                .andExpect(status().isBadRequest());

        verify(processor, never()).process(any(), any());
    }

    @Test
    void acknowledgesSignedNonFinalStatusWithoutFailingOrCreditingTheDeposit() throws Exception {
        String pending = signedBody(7_000, "Transaction is being processed.");

        mockMvc.perform(post("/api/v1/webhooks/momo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pending))
                .andExpect(status().isNoContent());

        verify(processor, never()).process(any(), any());
    }

    private static String signedBody(int resultCode, String message) {
        MomoNotification unsigned = new MomoNotification(
                "FIXTURE_PARTNER", "DEP_ABC12345", "1e89fbe4c24340f88458125afcd5bde4",
                100_000, "Nap tien vi DEP_ABC12345", "momo_wallet", 4_088_878_653L,
                resultCode, message, "qr", 1_727_000_000_000L, "", null
        );
        String signature = MomoSignature.signNotification(
                "fixture-access", "fixture-secret-key-32-characters", unsigned
        );
        return """
                {
                  "partnerCode":"FIXTURE_PARTNER",
                  "orderId":"DEP_ABC12345",
                  "requestId":"1e89fbe4c24340f88458125afcd5bde4",
                  "amount":100000,
                  "orderInfo":"Nap tien vi DEP_ABC12345",
                  "orderType":"momo_wallet",
                  "transId":4088878653,
                  "resultCode":%d,
                  "message":"%s",
                  "payType":"qr",
                  "responseTime":1727000000000,
                  "extraData":"",
                  "signature":"%s"
                }
                """.formatted(resultCode, message, signature);
    }

    private static MomoProperties properties() {
        return new MomoProperties(
                URI.create("https://test-payment.momo.vn/v2/gateway/api/create"),
                "FIXTURE_PARTNER", "fixture-access", "fixture-secret-key-32-characters",
                URI.create("https://shop.example.com/top-up"),
                URI.create("https://shop.example.com/api/v1/webhooks/momo"),
                Duration.ofSeconds(5), Duration.ofSeconds(30)
        );
    }
}
