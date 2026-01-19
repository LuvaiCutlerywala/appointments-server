package com.textellent.mcp.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textellent.mcp.security.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for logging audit events.
 * Logs tool calls with tenant context and redacted parameters.
 */
@Service
@Slf4j
public class AuditLogService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Log a tool call with redacted sensitive parameters.
     */
    public void logToolCall(String toolName, Map<String, Object> arguments, String status, String errorMessage) {
        Map<String, Object> auditLog = new HashMap<>();
        auditLog.put("timestamp", Instant.now().toString());
        auditLog.put("event", "TOOL_CALL");
        auditLog.put("tenantId", TenantContextHolder.getTenantId());
        auditLog.put("userId", TenantContextHolder.getUserId());
        auditLog.put("traceId", TenantContextHolder.getTraceId());
        auditLog.put("toolName", toolName);
        auditLog.put("status", status);

        // Redact sensitive parameters
        Map<String, Object> redactedArgs = redactSensitiveData(arguments);
        auditLog.put("arguments", redactedArgs);

        if (errorMessage != null) {
            auditLog.put("error", errorMessage);
        }

        try {
            String auditJson = objectMapper.writeValueAsString(auditLog);
            log.info("AUDIT: {}", auditJson);
        } catch (Exception e) {
            log.error("Failed to write audit log", e);
        }
    }

    /**
     * Log a successful tool call.
     */
    public void logSuccess(String toolName, Map<String, Object> arguments) {
        logToolCall(toolName, arguments, "SUCCESS", null);
    }

    /**
     * Log a failed tool call.
     */
    public void logFailure(String toolName, Map<String, Object> arguments, String errorMessage) {
        logToolCall(toolName, arguments, "FAILURE", errorMessage);
    }

    /**
     * Redact sensitive data from arguments.
     * Currently redacts: password, secret, token, apiKey, authCode
     */
    private Map<String, Object> redactSensitiveData(Map<String, Object> arguments) {
        if (arguments == null) {
            return null;
        }

        Map<String, Object> redacted = new HashMap<>(arguments);
        String[] sensitiveKeys = {"password", "secret", "token", "apiKey", "authCode", "api_key", "auth_code"};

        for (String key : sensitiveKeys) {
            if (redacted.containsKey(key)) {
                redacted.put(key, "***REDACTED***");
            }
        }

        return redacted;
    }
}
