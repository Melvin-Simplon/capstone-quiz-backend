package com.alderichoarau.azurequiz.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps how many API requests one caller may make per minute.
 *
 * <p>The application is deliberately open: no account, and a key that ships inside the frontend
 * bundle and therefore identifies rather than authenticates. What that leaves exposed is not the
 * data, which is training material carrying nothing personal, but the writes: a loop on session
 * creation fills the database and writes a blob per exported result. This is the control that
 * actually addresses it.
 *
 * <p>Writes are capped harder than reads because they are what costs something. Both counters are
 * per address, so one abusive caller cannot starve the others.
 *
 * <p>Runs before the API key check: a flood should be turned away at the cheapest possible point,
 * and it costs nothing to reject a caller who has no key either.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(value = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RequestRateLimiter reads;
    private final RequestRateLimiter writes;

    public RateLimitFilter(
            @Value("${app.rate-limit.requests-per-minute:120}") int requestsPerMinute,
            @Value("${app.rate-limit.writes-per-minute:20}") int writesPerMinute) {
        this.reads = new RequestRateLimiter(requestsPerMinute, Duration.ofMinutes(1));
        this.writes = new RequestRateLimiter(writesPerMinute, Duration.ofMinutes(1));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String caller = callerAddress(request);
        boolean isWrite = !"GET".equalsIgnoreCase(request.getMethod());
        RequestRateLimiter limiter = isWrite ? writes : reads;

        if (!limiter.tryAcquire(caller)) {
            response.setHeader("Retry-After", String.valueOf(limiter.retryAfterSeconds(caller)));
            response.sendError(429, "Too many requests");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * App Service terminates the connection upstream, so every request reaches the application from
     * the same address. Without reading the forwarded header, one counter would be shared by the
     * whole internet and the first caller to misbehave would lock everyone out.
     */
    private String callerAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(forwarded)) {
            return request.getRemoteAddr();
        }
        // "client:port, proxy, proxy": the client is first, and App Service appends its port.
        String client = forwarded.split(",")[0].trim();
        int port = client.lastIndexOf(':');
        return port > 0 && client.indexOf(':') == port ? client.substring(0, port) : client;
    }
}
