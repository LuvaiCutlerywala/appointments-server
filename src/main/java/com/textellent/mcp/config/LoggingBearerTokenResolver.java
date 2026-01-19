package com.textellent.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import javax.servlet.http.HttpServletRequest;

/**
 * Custom BearerTokenResolver that logs token extraction attempts.
 */
public class LoggingBearerTokenResolver implements BearerTokenResolver {

    private static final Logger logger = LoggerFactory.getLogger(LoggingBearerTokenResolver.class);
    private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        logger.info("=== BearerTokenResolver.resolve() called ===");
        logger.info("Request URI: {}", request.getRequestURI());
        logger.info("Authorization header: {}", request.getHeader("Authorization"));

        String token = delegate.resolve(request);

        if (token != null) {
            logger.info("Bearer token extracted successfully (length: {})", token.length());
            logger.debug("Token: {}", token);
        } else {
            logger.warn("No bearer token found in request");
        }

        return token;
    }
}
