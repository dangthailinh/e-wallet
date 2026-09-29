package com.github.yildizmy.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small, bounded fixed-window limiter for the mutation endpoints that can consume
 * credentials, money, inventory or provider capacity. The identity is deliberately
 * based on the servlet remote address (or an already-established authenticated
 * principal); forwarded headers are never trusted by this filter.
 *
 * <p>This is a process-local guard. Deployments with more than one backend instance
 * should put an equivalent shared limiter at the reverse proxy or use a shared store.
 * The limits are still useful locally and protect each instance during a dependency
 * outage or a burst before the proxy rule is applied.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@EnableConfigurationProperties(RateLimitFilter.RateLimitProperties.class)
public final class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    private static final String RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    private static final String RATE_LIMIT_POLICY = "X-RateLimit-Policy";
    private static final String RETRY_AFTER = "Retry-After";
    private static final String TOO_MANY_REQUESTS = "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Too many requests\",\"code\":\"RATE_LIMITED\"}";

    private final RateLimitProperties properties;
    private final Clock clock;
    private final Map<BucketKey, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * The provider keeps MVC slice tests (which intentionally do not import the
     * application's ClockConfig) self-contained while production still uses the
     * shared UTC clock bean when one is available.
     */
    @Autowired
    public RateLimitFilter(RateLimitProperties properties, ObjectProvider<Clock> clocks) {
        this(properties, clocks.getIfAvailable(Clock::systemUTC));
    }

    /** Constructor kept explicit for deterministic unit tests. */
    public RateLimitFilter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Route route = Route.match(request.getMethod(), request.getRequestURI());
        if (!properties.enabled() || route == null) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = properties.limitFor(route);
        String identity = trustedIdentity(request);
        Decision decision = consume(new BucketKey(route, identity), limit);
        response.setHeader(RATE_LIMIT_LIMIT, Integer.toString(limit));
        response.setHeader(RATE_LIMIT_REMAINING, Integer.toString(decision.remaining()));
        response.setHeader(RATE_LIMIT_POLICY, limit + ";w=" + ceilSeconds(properties.windowMillis()));

        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader(RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
            response.setHeader("Cache-Control", "no-store");
            response.setContentType("application/problem+json");
            response.getWriter().write(TOO_MANY_REQUESTS);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Decision consume(BucketKey key, int limit) {
        long now = clock.millis();
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(now));
        synchronized (bucket) {
            if (now - bucket.startedAtMillis >= properties.windowMillis()) {
                bucket.startedAtMillis = now;
                bucket.count.set(0);
            }
            if (bucket.count.get() >= limit) {
                long elapsed = Math.max(0L, now - bucket.startedAtMillis);
                long remainingMillis = Math.max(1L, properties.windowMillis() - elapsed);
                return new Decision(false, 0, ceilSeconds(remainingMillis));
            }
            int used = bucket.count.incrementAndGet();
            trimIfNecessary();
            return new Decision(true, Math.max(0, limit - used), 0);
        }
    }

    private void trimIfNecessary() {
        if (buckets.size() <= properties.maxEntries()) {
            return;
        }
        long cutoff = clock.millis() - properties.windowMillis();
        buckets.entrySet().removeIf(entry -> entry.getValue().startedAtMillis < cutoff);
        if (buckets.size() > properties.maxEntries()) {
            var iterator = buckets.entrySet().iterator();
            while (buckets.size() > properties.maxEntries() && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    private String trustedIdentity(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && StringUtils.hasText(authentication.getName())
                && !"anonymousUser".equals(authentication.getName())) {
            return "user:" + authentication.getName();
        }
        String remoteAddress = request.getRemoteAddr();
        return "ip:" + (StringUtils.hasText(remoteAddress) ? remoteAddress : "unknown");
    }

    private static long ceilSeconds(long milliseconds) {
        long seconds = milliseconds / 1000L;
        if (milliseconds % 1000L != 0 && seconds < Long.MAX_VALUE) {
            seconds++;
        }
        return Math.max(1L, seconds);
    }

    private record BucketKey(Route route, String identity) {
    }

    private static final class Bucket {
        private long startedAtMillis;
        private final AtomicInteger count = new AtomicInteger();

        private Bucket(long startedAtMillis) {
            this.startedAtMillis = startedAtMillis;
        }
    }

    private record Decision(boolean allowed, int remaining, long retryAfterSeconds) {
    }

    private enum Route {
        LOGIN,
        SIGNUP,
        REFRESH,
        LOGOUT,
        CHECKOUT,
        REVEAL,
        WEBHOOK;

        private static Route match(String method, String uri) {
            if (!"POST".equalsIgnoreCase(method) || uri == null) {
                return null;
            }
            return switch (uri) {
                case "/api/v1/auth/login" -> LOGIN;
                case "/api/v1/auth/signup" -> SIGNUP;
                case "/api/v1/auth/refresh" -> REFRESH;
                case "/api/v1/auth/logout" -> LOGOUT;
                case "/api/v1/orders" -> CHECKOUT;
                default -> {
                    if (uri.matches("^/api/v1/orders/[^/]+/items/[^/]+/reveal$")) {
                        yield REVEAL;
                    }
                    if (uri.startsWith("/api/v1/webhooks/")) {
                        yield WEBHOOK;
                    }
                    yield null;
                }
            };
        }
    }

    @ConfigurationProperties(prefix = "app.security.rate-limit")
    public record RateLimitProperties(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("60s") Duration window,
            @DefaultValue("5") int loginPerWindow,
            @DefaultValue("3") int signupPerWindow,
            @DefaultValue("10") int refreshPerWindow,
            @DefaultValue("10") int logoutPerWindow,
            @DefaultValue("20") int checkoutPerWindow,
            @DefaultValue("20") int revealPerWindow,
            @DefaultValue("60") int webhookPerWindow,
            @DefaultValue("10000") int maxEntries
    ) {
        public RateLimitProperties {
            Objects.requireNonNull(window, "rate-limit window is required");
            long windowMillis;
            try {
                windowMillis = window.toMillis();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("rate-limit window is too large", exception);
            }
            if (window.isZero() || window.isNegative() || windowMillis < 1L) {
                throw new IllegalArgumentException("rate-limit window must be positive");
            }
            if (loginPerWindow < 1 || signupPerWindow < 1 || refreshPerWindow < 1
                    || logoutPerWindow < 1 || checkoutPerWindow < 1 || revealPerWindow < 1
                    || webhookPerWindow < 1 || maxEntries < 1) {
                throw new IllegalArgumentException("rate-limit values must be positive");
            }
        }

        private int limitFor(Route route) {
            return switch (route) {
                case LOGIN -> loginPerWindow;
                case SIGNUP -> signupPerWindow;
                case REFRESH -> refreshPerWindow;
                case LOGOUT -> logoutPerWindow;
                case CHECKOUT -> checkoutPerWindow;
                case REVEAL -> revealPerWindow;
                case WEBHOOK -> webhookPerWindow;
            };
        }

        private long windowMillis() {
            return window.toMillis();
        }
    }
}
