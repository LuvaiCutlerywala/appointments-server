package com.textellent.mcp.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Utility class to extract custom claims from JWT tokens.
 * Supports multi-tenant architecture by extracting authCode and partnerClientCode from JWT.
 */
@Component
public class JwtClaimsExtractor {

    /**
     * Extract authCode from JWT token.
     * Checks multiple possible claim names for flexibility.
     * For ChatGPT Apps, falls back to user_name (email) as authCode.
     *
     * @param jwt The JWT token
     * @return The authCode, or null if not found
     */
    public String extractAuthCode(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        // Try different possible claim names
        String authCode = jwt.getClaimAsString("auth_code");
        if (authCode == null) {
            authCode = jwt.getClaimAsString("authCode");
        }
        if (authCode == null) {
            authCode = jwt.getClaimAsString("textellent_auth_code");
        }

        // For ChatGPT Apps and other OAuth2 clients, use user_name (email) as authCode
        if (authCode == null) {
            authCode = jwt.getClaimAsString("user_name");
        }

        // Final fallback to email claim
        if (authCode == null) {
            authCode = jwt.getClaimAsString("email");
        }

        return authCode;
    }

    /**
     * Extract partnerClientCode from JWT token.
     * Checks multiple possible claim names for flexibility.
     *
     * @param jwt The JWT token
     * @return The partnerClientCode, or null if not found
     */
    public String extractPartnerClientCode(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        // Try different possible claim names
        String partnerCode = jwt.getClaimAsString("partner_client_code");
        if (partnerCode == null) {
            partnerCode = jwt.getClaimAsString("partnerClientCode");
        }
        if (partnerCode == null) {
            partnerCode = jwt.getClaimAsString("textellent_partner_code");
        }

        return partnerCode;
    }

    /**
     * Extract tenant ID from JWT token.
     * Used for multi-tenancy and rate limiting.
     *
     * @param jwt The JWT token
     * @return The tenant ID, or null if not found
     */
    public String extractTenantId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        // Try different possible claim names
        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId == null) {
            tenantId = jwt.getClaimAsString("tenantId");
        }
        if (tenantId == null) {
            tenantId = jwt.getClaimAsString("organization_id");
        }
        if (tenantId == null) {
            tenantId = jwt.getClaimAsString("client_id");
        }

        return tenantId;
    }

    /**
     * Extract user ID from JWT token.
     * Used for audit logging.
     *
     * @param jwt The JWT token
     * @return The user ID, or null if not found
     */
    public String extractUserId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        // Try different possible claim names (standard + custom)
        String userId = jwt.getClaimAsString("sub");  // Standard JWT subject claim
        if (userId == null) {
            userId = jwt.getClaimAsString("user_id");
        }
        if (userId == null) {
            userId = jwt.getClaimAsString("userId");
        }
        if (userId == null) {
            userId = jwt.getClaimAsString("email");
        }

        return userId;
    }
}
