package com.alderichoarau.azurequiz.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {
    private static final String HEADER_NAME = "X-Api-Key";

    @Value("${app.security.api-key:}")
    private String expectedApiKey;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean disabled = !StringUtils.hasText(expectedApiKey);
        boolean isApiPath = request.getRequestURI().startsWith("/api/");
        boolean isPreflight = "OPTIONS".equalsIgnoreCase(request.getMethod());

        if (disabled || !isApiPath || isPreflight) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(HEADER_NAME);
        if (!matches(providedKey)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing " + HEADER_NAME);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean matches(String providedKey) {
        if (providedKey == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedApiKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8));
    }
}
