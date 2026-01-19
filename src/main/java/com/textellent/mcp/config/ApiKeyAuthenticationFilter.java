package com.textellent.mcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Simple API key authentication filter.
 * Validates X-API-Key header against configured value.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    @Value("${security.api-key:}")
    private String configuredApiKey;

    @Value("${security.api-key-scopes:textellent.read,textellent.write}")
    private String apiKeyScopes;

    private static final String API_KEY_HEADER = "X-API-Key";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Skip authentication for public endpoints
        if (requestPath.equals("/health") || requestPath.equals("/version") ||
            requestPath.startsWith("/actuator/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey != null && !apiKey.isEmpty()) {
            if (apiKey.equals(configuredApiKey)) {
                // Valid API key - create authentication with configured scopes
                List<SimpleGrantedAuthority> authorities = Arrays.stream(apiKeyScopes.split(","))
                    .map(String::trim)
                    .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                    .collect(Collectors.toList());

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken("api-key-user", null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Invalid API key\"}");
                return;
            }
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Missing API key\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
