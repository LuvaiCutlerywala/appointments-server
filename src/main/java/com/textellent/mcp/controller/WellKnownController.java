package com.textellent.mcp.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for .well-known endpoints used in OAuth 2.0 discovery.
 * Implements RFC 8414 - OAuth 2.0 Authorization Server Metadata.
 */
@RestController
@RequestMapping("/.well-known")
public class WellKnownController {

    private static final Logger logger = LoggerFactory.getLogger(WellKnownController.class);

    @Value("${OAUTH2_ISSUER_URI:https://staging.textellent.com/oauth2}")
    private String oauth2IssuerUri;

    /**
     * OAuth 2.0 Protected Resource Metadata (RFC 8414).
     * This is the standard endpoint that OAuth clients use for resource server discovery.
     * GET /.well-known/oauth-protected-resource
     */
    @GetMapping(value = "/oauth-protected-resource", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getOAuthProtectedResource(HttpServletRequest request) {
        logger.info("OAuth protected resource metadata request received");

        // Extract base URL from issuer URI
        String baseUrl = oauth2IssuerUri.replaceAll("/oauth2$", "");

        // Build the MCP SSE resource URL from the request
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String resourceUrl;

        if ((scheme.equals("http") && serverPort == 80) ||
            (scheme.equals("https") && serverPort == 443)) {
            resourceUrl = scheme + "://" + serverName + "/mcp/sse";
        } else {
            resourceUrl = scheme + "://" + serverName + ":" + serverPort + "/mcp/sse";
        }

        Map<String, Object> metadata = new HashMap<>();

        // OAuth 2.0 Authorization Server endpoints (from config)
        metadata.put("authorization_endpoint", baseUrl + "/oauth2/authorize");
        metadata.put("token_endpoint", baseUrl + "/oauth2/token");

        // Supported scopes
        metadata.put("scopes_supported", Arrays.asList("read", "write", "trust"));

        // Supported response types
        metadata.put("response_types_supported", Arrays.asList("code"));

        // Supported grant types
        metadata.put("grant_types_supported", Arrays.asList("authorization_code", "refresh_token"));

        // Token endpoint authentication methods
        metadata.put("token_endpoint_auth_methods_supported",
            Arrays.asList("client_secret_post", "client_secret_basic"));

        // Protected resource information (dynamic based on request)
        metadata.put("resource", resourceUrl);

        // MCP-specific metadata
        metadata.put("mcp_version", "2025-06-18");
        metadata.put("mcp_transport", "sse");

        logger.debug("Returning OAuth metadata with base URL: {}", baseUrl);

        return ResponseEntity.ok(metadata);
    }

    /**
     * OAuth 2.0 Authorization Server Metadata (RFC 8414).
     * Alternative endpoint for OAuth discovery.
     * GET /.well-known/oauth-authorization-server
     */
    @GetMapping(value = "/oauth-authorization-server", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getOAuthAuthorizationServer() {
        logger.info("OAuth authorization server metadata request received");

        // Extract base URL from issuer URI
        String baseUrl = oauth2IssuerUri.replaceAll("/oauth2$", "");

        Map<String, Object> metadata = new HashMap<>();

        // Issuer (from config)
        metadata.put("issuer", baseUrl);

        // OAuth 2.0 endpoints (from config)
        metadata.put("authorization_endpoint", baseUrl + "/oauth2/authorize");
        metadata.put("token_endpoint", baseUrl + "/oauth2/token");

        // Supported features
        metadata.put("scopes_supported", Arrays.asList("read", "write", "trust"));
        metadata.put("response_types_supported", Arrays.asList("code"));
        metadata.put("grant_types_supported", Arrays.asList("authorization_code", "refresh_token"));
        metadata.put("token_endpoint_auth_methods_supported",
            Arrays.asList("client_secret_post", "client_secret_basic"));

        logger.debug("Returning OAuth authorization server metadata with issuer: {}", baseUrl);

        return ResponseEntity.ok(metadata);
    }
}
