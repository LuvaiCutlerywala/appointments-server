package com.textellent.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Security configuration for the MCP server.
 * Supports two modes:
 * 1. OAuth2 JWT (production) - validates JWT tokens with scopes
 * 2. API Key (simple) - validates custom API key header
 * Note: Local mode (no security) is handled by LocalSecurityConfig
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@ConditionalOnExpression("'${security.mode:oauth2}' != 'local'")
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    @Value("${security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Value("${security.oauth2.resourceserver.jwt.signing-key:textellent.123}")
    private String jwtSigningKey;

    @Value("${security.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${security.mode:oauth2}")
    private String securityMode;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        logger.info("Configuring security with mode: {}", securityMode);

        http
            .cors()
            .and()
            .csrf().disable()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        // Configure authentication based on mode
        if ("local".equals(securityMode)) {
            logger.info("Configuring LOCAL mode - permit all");
            http.authorizeRequests().anyRequest().permitAll();
        } else if ("apikey".equals(securityMode)) {
            logger.info("Configuring API KEY mode");
            http.addFilterBefore(new ApiKeyAuthenticationFilter(),
                org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
            http.authorizeRequests()
                .antMatchers("/health", "/actuator/health", "/version").permitAll()
                .antMatchers("/mcp", "/mcp/**").authenticated()
                .anyRequest().authenticated();
        } else if ("oauth2".equals(securityMode)) {
            logger.info("Configuring OAUTH2 JWT mode");

            // Configure OAuth2 resource server with JWT
            http
                .authorizeRequests()
                    .antMatchers("/health", "/actuator/health", "/version").permitAll()
                    // Allow .well-known endpoints for OAuth discovery (RFC 8414)
                    .antMatchers("/.well-known/**").permitAll()
                    // Allow GET /mcp and GET /mcp/sse for metadata discovery (needed by ChatGPT Apps)
                    .antMatchers(org.springframework.http.HttpMethod.GET, "/mcp", "/mcp/sse").permitAll()
                    // All other /mcp endpoints require authentication
                    .antMatchers("/mcp", "/mcp/**").authenticated()
                    .anyRequest().authenticated()
                .and()
                .oauth2ResourceServer()
                    .bearerTokenResolver(bearerTokenResolver())
                    .jwt()
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter());

            logger.info("OAuth2 resource server configured with JWT decoder and custom bearer token resolver");
        }
    }

    @Bean
    @ConditionalOnProperty(name = "security.mode", havingValue = "oauth2")
    public org.springframework.security.oauth2.server.resource.web.BearerTokenResolver bearerTokenResolver() {
        logger.info("=== Creating Custom BearerTokenResolver ===");
        return new LoggingBearerTokenResolver();
    }

    @Bean
    @ConditionalOnProperty(name = "security.mode", havingValue = "oauth2")
    public JwtDecoder jwtDecoder() {
        logger.info("=== Creating JWT Decoder ===");
        logger.info("Signing key configured: {}", jwtSigningKey != null && !jwtSigningKey.isEmpty());

        // Use symmetric key (HS256) matching OAuth2 server
        if (jwtSigningKey == null || jwtSigningKey.isEmpty()) {
            throw new IllegalStateException("JWT signing key must be configured for OAuth2 mode");
        }

        logger.info("Configuring JWT decoder with HMAC-SHA256 symmetric key");

        // Pad the key to 32 bytes (256 bits) if it's too short for HS256
        byte[] keyBytes = jwtSigningKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            logger.warn("Signing key is only {} bytes, padding to 32 bytes for HS256", keyBytes.length);
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, keyBytes.length);
            // Fill remaining bytes with zeros (padding)
            keyBytes = paddedKey;
        }

        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).build();

        // Don't add ANY validators - keep it simple
        // The OAuth2 server JWT doesn't have standard claims like 'iss' or 'aud'
        logger.info("JWT decoder created successfully (no validators)");

        // Wrap with logging decoder to debug issues
        return new LoggingJwtDecoder(decoder);
    }

    /**
     * Custom JWT authentication converter to properly extract scopes as authorities.
     * This converter handles the JWT's scope claim and converts it to Spring Security authorities.
     */
    @Bean
    @ConditionalOnProperty(name = "security.mode", havingValue = "oauth2")
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        logger.info("=== Creating JWT Authentication Converter ===");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        // Custom converter that extracts scopes from JWT
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            logger.debug("Converting JWT to authorities");
            logger.debug("JWT claims: {}", jwt.getClaims());

            Collection<GrantedAuthority> authorities = new ArrayList<>();

            // Extract scope claim - it can be either a String or Collection
            Object scopeClaim = jwt.getClaim("scope");
            logger.debug("Scope claim: {} (type: {})", scopeClaim,
                scopeClaim != null ? scopeClaim.getClass().getSimpleName() : "null");

            if (scopeClaim instanceof Collection) {
                // Scope is already a collection (most common in Spring OAuth2)
                Collection<?> scopes = (Collection<?>) scopeClaim;
                for (Object scope : scopes) {
                    String authority = "SCOPE_" + scope.toString();
                    authorities.add(new SimpleGrantedAuthority(authority));
                    logger.debug("Added authority: {}", authority);
                }
            } else if (scopeClaim instanceof String) {
                // Scope is a space-delimited string
                String scopeString = (String) scopeClaim;
                String[] scopes = scopeString.split(" ");
                for (String scope : scopes) {
                    String authority = "SCOPE_" + scope;
                    authorities.add(new SimpleGrantedAuthority(authority));
                    logger.debug("Added authority: {}", authority);
                }
            }

            logger.info("Total authorities extracted: {}", authorities.size());
            authorities.forEach(auth -> logger.debug("  - {}", auth.getAuthority()));

            return authorities;
        });

        logger.info("JWT authentication converter created successfully");
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Parse allowed origins from config
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOrigins(origins);

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Tenant-ID",
            "X-Trace-ID",
            "MCP-Protocol-Version",
            "authCode",
            "partnerClientCode"
        ));
        configuration.setExposedHeaders(Arrays.asList("X-Trace-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
