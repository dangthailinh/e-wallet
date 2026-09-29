package com.github.yildizmy.config;

import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogRedactionConverterTest {

    @Test
    void redactsBearerCookiesAndSensitiveAssignments() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("Authorization: Bearer access-token Cookie=session-cookie signature=provider-signature secret=inventory-secret requestId=req-123");

        String output = LogRedactionConverter.redactMessage(event.getFormattedMessage());

        assertThat(output)
                .contains("Authorization: <redacted>")
                .contains("Cookie=<redacted>")
                .contains("signature=<redacted>")
                .contains("secret=<redacted>")
                .contains("requestId=req-123")
                .doesNotContain("access-token", "session-cookie", "provider-signature", "inventory-secret");
    }

    @Test
    void leavesOrdinaryOperationalMessagesReadable() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("checkout result=created requestId=req-456");

        assertThat(LogRedactionConverter.redactMessage(event.getFormattedMessage()))
                .isEqualTo("checkout result=created requestId=req-456");
    }
}
