package com.textellent.mcp.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Holds tenant and user context for the current request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantContext {
    private String tenantId;
    private String userId;
    private String traceId;
}
