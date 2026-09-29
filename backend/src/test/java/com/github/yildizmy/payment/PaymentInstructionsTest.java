package com.github.yildizmy.payment;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentInstructionsTest {

    @Test
    void supportsExactlyOneSafeInstructionMode() {
        assertThat(PaymentInstructions.redirect(URI.create("https://pay.example.test/order/1")).redirectUrl())
                .hasScheme("https");
        assertThat(PaymentInstructions.qr("000201010212merchant-payload").qrPayload())
                .isEqualTo("000201010212merchant-payload");

        assertThatThrownBy(() -> PaymentInstructions.redirect(URI.create("http://pay.example.test/order/1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentInstructions.redirect(URI.create("https://user@pay.example.test/order/1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentInstructions(null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentInstructions(
                URI.create("https://pay.example.test/order/1"), "qr-payload"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
