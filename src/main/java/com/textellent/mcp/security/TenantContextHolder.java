package com.textellent.mcp.security;

/**
 * ThreadLocal holder for tenant context.
 * Ensures tenant isolation across requests.
 */
public class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    public static void setContext(TenantContext context) {
        CONTEXT.set(context);
    }

    public static TenantContext getContext() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static String getTenantId() {
        TenantContext context = getContext();
        return context != null ? context.getTenantId() : null;
    }

    public static String getUserId() {
        TenantContext context = getContext();
        return context != null ? context.getUserId() : null;
    }

    public static String getTraceId() {
        TenantContext context = getContext();
        return context != null ? context.getTraceId() : null;
    }
}
