package com.github.yildizmy.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.pattern.ClassicConverter;

import java.util.regex.Pattern;

/** Redacts credentials and transport secrets before they reach any configured appender. */
public final class LogRedactionConverter extends ClassicConverter {

    private static final Pattern BEARER = Pattern.compile("(?i)(\\bBearer\\s+)[^\\s,;]+");
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i)(\\b(?:authorization|cookie|set-cookie|token|signature|secret|password|api[_-]?key)\\b\\s*[:=]\\s*)(?:Bearer\\s+)?[^\\s,;]+"
    );

    @Override
    public String convert(ILoggingEvent event) {
        return redactMessage(event.getFormattedMessage());
    }

    static String redactMessage(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String redacted = BEARER.matcher(value).replaceAll("$1<redacted>");
        return SENSITIVE_FIELD.matcher(redacted).replaceAll("$1<redacted>");
    }
}
