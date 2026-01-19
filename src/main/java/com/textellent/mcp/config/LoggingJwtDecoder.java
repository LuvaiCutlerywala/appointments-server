package com.textellent.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Custom JwtDecoder wrapper that logs all decoding attempts.
 */
public class LoggingJwtDecoder implements JwtDecoder {

    private static final Logger logger = LoggerFactory.getLogger(LoggingJwtDecoder.class);
    private final JwtDecoder delegate;

    public LoggingJwtDecoder(JwtDecoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        logger.info("=== JwtDecoder.decode() called ===");
        logger.info("Token length: {}", token != null ? token.length() : "null");

        try {
            Jwt jwt = delegate.decode(token);
            logger.info("JWT decoded successfully!");
            logger.info("JWT subject: {}", jwt.getSubject());
            logger.info("JWT claims: {}", jwt.getClaims());
            return jwt;
        } catch (JwtException e) {
            logger.error("JWT decoding FAILED: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during JWT decoding: {}", e.getMessage(), e);
            throw new JwtException("Failed to decode JWT", e);
        }
    }
}
