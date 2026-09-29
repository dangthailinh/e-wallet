package com.github.yildizmy.payment.momo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "app.payment.momo")
public record MomoProperties(
        URI createUrl,
        String partnerCode,
        String accessKey,
        String secretKey,
        URI redirectUrl,
        URI ipnUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    public MomoProperties {
        createUrl = requireHttps(createUrl, "createUrl");
        if (!isMomoHost(createUrl.getHost())) {
            throw new IllegalArgumentException("createUrl must use an official momo.vn host");
        }
        partnerCode = requireText(partnerCode, "partnerCode", 2, 50);
        accessKey = requireText(accessKey, "accessKey", 8, 200);
        secretKey = requireText(secretKey, "secretKey", 16, 500);
        redirectUrl = requireHttps(redirectUrl, "redirectUrl");
        ipnUrl = requireHttps(ipnUrl, "ipnUrl");
        connectTimeout = requirePositive(connectTimeout, "connectTimeout");
        readTimeout = requirePositive(readTimeout, "readTimeout");
        if (readTimeout.compareTo(Duration.ofSeconds(30)) < 0) {
            throw new IllegalArgumentException("readTimeout must be at least 30 seconds for MoMo");
        }
    }

    /** MoMo keeps queryTransaction on the same gateway API path as create. */
    public URI queryUrl() {
        String path = createUrl.getPath();
        int slash = path.lastIndexOf('/');
        String base = slash < 0 ? "/" : path.substring(0, slash + 1);
        return URI.create(createUrl.getScheme() + "://" + createUrl.getAuthority() + base + "query");
    }

    private static URI requireHttps(URI value, String fieldName) {
        URI uri = Objects.requireNonNull(value, fieldName + " is required");
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(fieldName + " must be a safe HTTPS URL");
        }
        return uri;
    }

    private static boolean isMomoHost(String host) {
        String normalized = host.toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("momo.vn") || normalized.endsWith(".momo.vn");
    }

    private static String requireText(String value, String fieldName, int minimum, int maximum) {
        String text = Objects.requireNonNull(value, fieldName + " is required");
        if (!text.equals(text.trim()) || text.length() < minimum || text.length() > maximum
                || text.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(fieldName + " has invalid format");
        }
        return text;
    }

    private static Duration requirePositive(Duration value, String fieldName) {
        Duration duration = Objects.requireNonNull(value, fieldName + " is required");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return duration;
    }

    @Override
    public String toString() {
        return "MomoProperties[createUrl=" + createUrl
                + ", partnerCode=" + partnerCode
                + ", accessKey=REDACTED, secretKey=REDACTED"
                + ", redirectUrl=" + redirectUrl
                + ", ipnUrl=" + ipnUrl
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
