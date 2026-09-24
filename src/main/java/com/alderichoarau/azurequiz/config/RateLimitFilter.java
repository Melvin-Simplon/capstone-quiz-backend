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

    private String callerAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(forwarded)) {
            return request.getRemoteAddr();
        }
        String client = forwarded.split(",")[0].trim();
        int port = client.lastIndexOf(':');
        return port > 0 && client.indexOf(':') == port ? client.substring(0, port) : client;
    }
}
