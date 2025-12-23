package com.textellent.mcp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textellent.mcp.models.*;
import com.textellent.mcp.registry.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Controller handling JSON-RPC 2.0 requests for MCP tools.
 */
@RestController
@RequestMapping("/mcp")
public class McpController {

    private static final Logger logger = LoggerFactory.getLogger(McpController.class);

    @Autowired
    private McpToolRegistry toolRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Main MCP endpoint handling JSON-RPC requests.
     * POST /mcp
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<McpRpcResponse> handleMcpRequest(
            @RequestBody McpRpcRequest request,
            @RequestHeader(value = "authCode", required = false) String authCode,
            @RequestHeader(value = "partnerClientCode", required = false) String partnerClientCode) {

        logger.info("Received MCP request: method={}, id={}", request.getMethod(), request.getId());

        try {
            // Validate JSON-RPC version
            if (!"2.0".equals(request.getJsonrpc())) {
                return createErrorResponse(request.getId(), -32600, "Invalid JSON-RPC version");
            }

            // Route based on method
            String method = request.getMethod();
            if (method == null) {
                return createErrorResponse(request.getId(), -32600, "Method is required");
            }

            switch (method) {
                case "initialize":
                    return handleInitialize(request);
                case "tools/list":
                    return handleToolsList(request);
                case "tools/call":
                    return handleToolsCall(request, authCode, partnerClientCode);
                default:
                    return createErrorResponse(request.getId(), -32601, "Method not found: " + method);
            }

        } catch (Exception e) {
            logger.error("Error handling MCP request", e);
            return createErrorResponse(request.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * Handle initialize method - MCP protocol handshake.
     */
    private ResponseEntity<McpRpcResponse> handleInitialize(McpRpcRequest request) {
        logger.info("Handling initialize request");

        try {
            Map<String, Object> result = new HashMap<>();
            result.put("protocolVersion", "2025-06-18");

            Map<String, Object> capabilities = new HashMap<>();
            capabilities.put("tools", new HashMap<>());
            result.put("capabilities", capabilities);

            Map<String, Object> serverInfo = new HashMap<>();
            serverInfo.put("name", "textellent-mcp-server");
            serverInfo.put("version", "1.0.0");
            result.put("serverInfo", serverInfo);

            McpRpcResponse response = new McpRpcResponse(request.getId(), result);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error in initialize", e);
            return createErrorResponse(request.getId(), -32603, "Failed to initialize: " + e.getMessage());
        }
    }

    /**
     * Handle tools/list method - returns all available tools.
     */
    private ResponseEntity<McpRpcResponse> handleToolsList(McpRpcRequest request) {
        logger.info("Handling tools/list request");

        try {
            List<McpToolDefinition> tools = toolRegistry.getAllToolDefinitions();

            Map<String, Object> result = new HashMap<>();
            result.put("tools", tools);

            McpRpcResponse response = new McpRpcResponse(request.getId(), result);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error listing tools", e);
            return createErrorResponse(request.getId(), -32603, "Failed to list tools: " + e.getMessage());
        }
    }

    /**
     * Handle tools/call method - executes a specific tool.
     */
    private ResponseEntity<McpRpcResponse> handleToolsCall(
            McpRpcRequest request, String authCode, String partnerClientCode) {

        logger.info("Handling tools/call request");

        try {
            Map<String, Object> params = request.getParams();
            if (params == null) {
                return createErrorResponse(request.getId(), -32602, "Params are required");
            }

            // Extract tool call parameters
            String toolName = (String) params.get("name");
            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

            if (toolName == null) {
                return createErrorResponse(request.getId(), -32602, "Tool name is required");
            }

            // Validate auth headers
            if (authCode == null || authCode.isEmpty()) {
                return createErrorResponse(request.getId(), -32001, "authCode header is required");
            }

            // partnerClientCode is optional - backend API can work with just authCode

            // Check if tool exists
            if (!toolRegistry.hasTool(toolName)) {
                return createErrorResponse(request.getId(), -32602, "Unknown tool: " + toolName);
            }

            // Execute the tool
            Object result = toolRegistry.execute(toolName, arguments, authCode, partnerClientCode);

            // Parse result if it's a JSON string
            Object parsedResult;
            if (result instanceof String) {
                try {
                    parsedResult = objectMapper.readValue((String) result, Object.class);
                } catch (Exception e) {
                    parsedResult = result;
                }
            } else {
                parsedResult = result;
            }

            // Extract the actual data from the API response if it follows the standard Textellent format
            Object dataToReturn = parsedResult;
            if (parsedResult instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) parsedResult;
                // If response has 'data' field, extract it for Claude Desktop
                if (responseMap.containsKey("data")) {
                    dataToReturn = responseMap.get("data");
                }
            }

            // Format response according to MCP protocol - content must be array of content items with type
            List<Map<String, Object>> contentArray = new ArrayList<>();
            Map<String, Object> contentItem = new HashMap<>();
            contentItem.put("type", "text");
            contentItem.put("text", objectMapper.writeValueAsString(dataToReturn));
            contentArray.add(contentItem);

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("content", contentArray);
            resultMap.put("isError", false);

            McpRpcResponse response = new McpRpcResponse(request.getId(), resultMap);
            return ResponseEntity.ok(response);

        } catch (org.everit.json.schema.ValidationException e) {
            logger.error("Validation error", e);
            return createErrorResponse(request.getId(), -32602, "Invalid arguments: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument", e);
            return createErrorResponse(request.getId(), -32602, e.getMessage());
        } catch (Exception e) {
            logger.error("Error executing tool", e);
            return createErrorResponse(request.getId(), -32603, "Tool execution failed: " + e.getMessage());
        }
    }

    /**
     * Create an error response.
     */
    private ResponseEntity<McpRpcResponse> createErrorResponse(Object id, int code, String message) {
        McpRpcResponse.McpRpcError error = new McpRpcResponse.McpRpcError(code, message);
        McpRpcResponse response = new McpRpcResponse(id, error);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "textellent-mcp-server");
        health.put("version", "1.0.0");
        health.put("toolsRegistered", toolRegistry.getAllToolDefinitions().size());
        return ResponseEntity.ok(health);
    }
}
