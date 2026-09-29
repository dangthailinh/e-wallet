package com.github.yildizmy.payment.momo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MomoSignatureTest {

    @Test
    void signsOfficialCreatePaymentFieldOrderWithHmacSha256() {
        MomoCreateRequest request = new MomoCreateRequest(
                "FIXTURE_PARTNER",
                "captureWallet",
                "https://shop.example.com/api/v1/webhooks/momo",
                "https://shop.example.com/top-up",
                "DEP_ABC12345",
                1_000,
                "Nap tien vi DEP_ABC12345",
                "1e89fbe4c24340f88458125afcd5bde4",
                "",
                "vi",
                null
        );

        assertThat(MomoSignature.canonicalCreateRequest("fixture-access", request)).isEqualTo(
                "accessKey=fixture-access&amount=1000&extraData=&ipnUrl=https://shop.example.com/api/v1/webhooks/momo"
                        + "&orderId=DEP_ABC12345&orderInfo=Nap tien vi DEP_ABC12345&partnerCode=FIXTURE_PARTNER"
                        + "&redirectUrl=https://shop.example.com/top-up&requestId=1e89fbe4c24340f88458125afcd5bde4"
                        + "&requestType=captureWallet"
        );
        assertThat(MomoSignature.signCreate("fixture-access", "fixture-secret-key-32-characters", request))
                .isEqualTo("eaf88a758f49712bca478da3956b3262d49cd2814a5523019dd1a759be0b00be");
    }
}
