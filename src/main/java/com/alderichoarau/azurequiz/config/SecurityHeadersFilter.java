package com.alderichoarau.azurequiz.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sets the response headers a browser needs in order to protect the caller, which Spring Boot does
 * not send on its own once Spring Security is not on the classpath.
 *
 * <p>They matter here even though this is a JSON API: the frontend is a browser, so a wrong content
 * type sniffed as HTML, a response framed by another page, or a first request downgraded to plain
 * HTTP are all reachable from where the caller sits.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        // An API answers no page, so nothing legitimate loads from it.
        response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");

        // Only over TLS: sent over plain HTTP the header is ignored, and promising a year of
        // HTTPS from a connection that was not one is how a local setup gets bricked.
        //
        // isSecure() alone is not enough behind App Service, which terminates TLS upstream and
        // hands the application a plain HTTP request carrying X-Forwarded-Proto. Reading it here
        // rather than switching on forwarded headers globally keeps the change to this concern.
        boolean overTls =
                request.isSecure()
                        || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        if (overTls) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        filterChain.doFilter(request, response);
    }
}
