package com.textellent.mcp.filter;

import com.textellent.mcp.security.TenantContext;
import com.textellent.mcp.security.TenantContextHolder;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Filter to extract tenant context from JWT claims and set up MDC logging context.
 * Runs after Spring Security authentication.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-ID";
    private static final String TENANT_ID_HEADER = "X-Tenant-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // Generate or extract trace ID
            String traceId = request.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isEmpty()) {
                traceId = UUID.randomUUID().toString();
            }

            // Extract tenant ID and user ID from JWT or header
            String tenantId = extractTenantId(request);
            String userId = extractUserId();

            // Set up tenant context
            TenantContext context = new TenantContext(tenantId, userId, traceId);
            TenantContextHolder.setContext(context);

            // Set up MDC for logging
            MDC.put("traceId", traceId);
            MDC.put("tenantId", tenantId != null ? tenantId : "unknown");
            MDC.put("userId", userId != null ? userId : "unknown");

            // Add trace ID to response header
            response.setHeader(TRACE_ID_HEADER, traceId);

            filterChain.doFilter(request, response);
        } finally {
            // Clean up context
            TenantContextHolder.clear();
            MDC.clear();
        }
    }

    private String extractTenantId(HttpServletRequest request) {
        // First try header (for API key mode or explicit override)
        String tenantId = request.getHeader(TENANT_ID_HEADER);
        if (tenantId != null && !tenantId.isEmpty()) {
            return tenantId;
        }

        // Then try JWT claim
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            // Try various common claim names
            Object tenantClaim = jwt.getClaim("tenant_id");
            if (tenantClaim == null) {
                tenantClaim = jwt.getClaim("tenantId");
            }
            if (tenantClaim == null) {
                tenantClaim = jwt.getClaim("organization_id");
            }
            if (tenantClaim != null) {
                return tenantClaim.toString();
            }
        }

        return null;
    }

    private String extractUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String subject = jwt.getSubject();
            if (subject != null) {
                return subject;
            }
        }
        return null;
    }
}
