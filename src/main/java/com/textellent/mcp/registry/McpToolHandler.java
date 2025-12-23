package com.textellent.mcp.registry;

import java.util.Map;

/**
 * Functional interface for MCP tool handlers.
 * Each handler takes arguments and returns a result object.
 */
@FunctionalInterface
public interface McpToolHandler {

    /**
     * Execute the tool with the given arguments.
     *
     * @param arguments The tool arguments
     * @param authCode The authentication code from headers
     * @param partnerClientCode The partner client code from headers
     * @return The result of the tool execution
     * @throws Exception if execution fails
     */
    Object execute(Map<String, Object> arguments, String authCode, String partnerClientCode) throws Exception;
}
