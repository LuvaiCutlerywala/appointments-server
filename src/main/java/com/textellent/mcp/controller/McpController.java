package com.textellent.mcp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textellent.mcp.audit.AuditLogService;
import com.textellent.mcp.models.*;
import com.textellent.mcp.ratelimit.RateLimitService;
import com.textellent.mcp.registry.McpToolRegistry;
import com.textellent.mcp.security.JwtClaimsExtractor;
import com.textellent.mcp.services.ActionListService;
import com.textellent.mcp.sse.SseSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced MCP Controller with OAuth2, rate limiting, SSE support, and audit logging.
 * Handles JSON-RPC 2.0 requests for MCP tools with security and multi-tenancy.
 */
@RestController
@RequestMapping("/mcp")
public class McpController {

    private static final Logger logger = LoggerFactory.getLogger(McpController.class);

    @Autowired
    private McpToolRegistry toolRegistry;

    @Autowired
    private ActionListService actionListService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired(required = false)
    private JwtClaimsExtractor jwtClaimsExtractor;

    @Autowired
    private SseSessionManager sseSessionManager;

    @Value("${mcp.server.name:textellent-mcp-server}")
    private String serverName;

    @Value("${mcp.server.version:1.0.0}")
    private String serverVersion;

    @Value("${mcp.server.protocol-version:2025-06-18}")
    private String protocolVersion;

    @Value("${OAUTH2_ISSUER_URI:https://staging.textellent.com/oauth2}")
    private String oauth2IssuerUri;

    /**
     * MCP Server metadata endpoint.
     * Returns server information and OAuth2 configuration for discovery.
     * GET /mcp
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getServerMetadata() {
        Map<String, Object> metadata = new HashMap<>();

        // Extract base URL from issuer URI
        String baseUrl = oauth2IssuerUri.replaceAll("/oauth2$", "");

        // Server info
        metadata.put("name", serverName);
        metadata.put("version", serverVersion);
        metadata.put("protocolVersion", protocolVersion);

        // Capabilities
        Map<String, Object> capabilities = new HashMap<>();

        // OAuth2 authentication
        Map<String, Object> authCapabilities = new HashMap<>();
        Map<String, Object> oauth2Config = new HashMap<>();
        oauth2Config.put("authorizationUrl", baseUrl + "/oauth2/authorize");
        oauth2Config.put("tokenUrl", baseUrl + "/oauth2/token");
        oauth2Config.put("scopes", Arrays.asList("read", "write", "trust"));

        authCapabilities.put("oauth2", oauth2Config);
        capabilities.put("authentication", authCapabilities);

        // Transport capabilities
        Map<String, Object> transportCapabilities = new HashMap<>();
        transportCapabilities.put("sse", true);
        transportCapabilities.put("http", true);
        capabilities.put("transports", transportCapabilities);

        // Tools capabilities (explicit list/call support)
        Map<String, Object> toolsCapabilities = new HashMap<>();
        toolsCapabilities.put("list", new HashMap<>());
        toolsCapabilities.put("call", new HashMap<>());
        capabilities.put("tools", toolsCapabilities);

        // Resources capabilities (explicit list/read support)
        Map<String, Object> resourcesCapabilities = new HashMap<>();
        resourcesCapabilities.put("list", new HashMap<>());
        resourcesCapabilities.put("read", new HashMap<>());
        capabilities.put("resources", resourcesCapabilities);

        metadata.put("capabilities", capabilities);

        // Endpoints
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("sse", "/mcp/sse");
        endpoints.put("http", "/mcp");
        metadata.put("endpoints", endpoints);

        logger.info("Metadata request received");
        return ResponseEntity.ok(metadata);
    }

    /**
     * Main MCP endpoint handling JSON-RPC requests over HTTP.
     * Supports MCP Streamable HTTP transport with proper headers.
     * POST /mcp
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<McpRpcResponse> handleMcpRequest(
            @RequestBody McpRpcRequest request,
            @RequestHeader(value = "MCP-Protocol-Version", required = false) String mcpProtocolVersion,
            @RequestHeader(value = "authCode", required = false) String authCode,
            @RequestHeader(value = "partnerClientCode", required = false) String partnerClientCode,
            Authentication authentication) {

        logger.info("Received MCP request: method={}, id={}, mcpVersion={}",
            request.getMethod(), request.getId(), mcpProtocolVersion);

        try {
            return processJsonRpcRequest(request, authCode, partnerClientCode, authentication);
        } catch (Exception e) {
            logger.error("Error handling MCP request", e);
            return createErrorResponse(request.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * SSE metadata endpoint for discovery (returns JSON when Accept is not text/event-stream).
     * This allows ChatGPT Apps to discover OAuth2 configuration before establishing SSE connection.
     * GET /mcp/sse (with Accept: application/json)
     */
    @GetMapping(value = "/sse", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getSseMetadata() {
        logger.info("SSE metadata request received");
        return getServerMetadata();
    }

    /**
     * SSE endpoint for MCP protocol communication.
     * Creates a persistent connection for bidirectional JSON-RPC communication.
     * GET /mcp/sse (with Accept: text/event-stream)
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<McpRpcResponse>> handleSseStream(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            Authentication authentication) {

        // Extract user identity for session management
        String userIdentity = "anonymous";
        if (authentication != null && authentication.getName() != null) {
            userIdentity = authentication.getName();
        }

        // Create new session
        String sessionId = sseSessionManager.createSession(userIdentity);
        logger.info("SSE stream established - Session: {}, User: {}", sessionId, userIdentity);

        // Get the sink for this session
        reactor.core.publisher.Sinks.Many<McpRpcResponse> sink = sseSessionManager.getSessionSink(sessionId);

        if (sink == null) {
            logger.error("Failed to create session sink");
            return Flux.empty();
        }

        // Create the response flux with keepalive pings
        Flux<ServerSentEvent<McpRpcResponse>> keepalive = Flux.interval(Duration.ofSeconds(30))
            .map(seq -> {
                // Send ping to keep connection alive
                Map<String, Object> pingData = new HashMap<>();
                pingData.put("type", "ping");
                pingData.put("timestamp", System.currentTimeMillis());

                McpRpcResponse pingResponse = new McpRpcResponse("ping-" + seq, pingData);

                return ServerSentEvent.<McpRpcResponse>builder()
                    .id(sessionId + "-ping-" + seq)
                    .event("ping")
                    .data(pingResponse)
                    .build();
            });

        // Convert sink to flux and merge with keepalive
        Flux<ServerSentEvent<McpRpcResponse>> messageStream = sink.asFlux()
            .map(response -> ServerSentEvent.<McpRpcResponse>builder()
                .id(sessionId + "-msg-" + response.getId())
                .event("message")
                .data(response)
                .build());

        // Send initial connection event with session ID and OAuth2 capabilities
        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", serverName);
        serverInfo.put("version", serverVersion);

        // Add capabilities (authentication, tools, resources)
        Map<String, Object> capabilities = new HashMap<>();
        Map<String, Object> authCapabilities = new HashMap<>();

        // Extract base URL from issuer URI
        String baseUrl = oauth2IssuerUri.replaceAll("/oauth2$", "");

        // OAuth2 configuration
        Map<String, Object> oauth2Config = new HashMap<>();
        oauth2Config.put("authorizationUrl", baseUrl + "/oauth2/authorize");
        oauth2Config.put("tokenUrl", baseUrl + "/oauth2/token");
        oauth2Config.put("scopes", Arrays.asList("read", "write", "trust"));

        authCapabilities.put("oauth2", oauth2Config);
        capabilities.put("authentication", authCapabilities);

        // Tools capabilities
        Map<String, Object> toolsCapabilities = new HashMap<>();
        toolsCapabilities.put("list", new HashMap<>());
        toolsCapabilities.put("call", new HashMap<>());
        capabilities.put("tools", toolsCapabilities);

        // Resources capabilities
        Map<String, Object> resourcesCapabilities = new HashMap<>();
        resourcesCapabilities.put("list", new HashMap<>());
        resourcesCapabilities.put("read", new HashMap<>());
        capabilities.put("resources", resourcesCapabilities);

        serverInfo.put("capabilities", capabilities);

        Map<String, Object> connectionData = new HashMap<>();
        connectionData.put("sessionId", sessionId);
        connectionData.put("protocolVersion", protocolVersion);
        connectionData.put("serverInfo", serverInfo);

        McpRpcResponse connectionEvent = new McpRpcResponse("connection", connectionData);

        ServerSentEvent<McpRpcResponse> initialEvent = ServerSentEvent.<McpRpcResponse>builder()
            .id(sessionId + "-init")
            .event("connected")
            .data(connectionEvent)
            .build();

        // Merge initial event, messages, and keepalive
        return Flux.concat(
            Flux.just(initialEvent),
            Flux.merge(messageStream, keepalive)
        ).doOnCancel(() -> {
            logger.info("SSE stream cancelled for session: {}", sessionId);
            sseSessionManager.closeSession(sessionId);
        }).doOnComplete(() -> {
            logger.info("SSE stream completed for session: {}", sessionId);
            sseSessionManager.closeSession(sessionId);
        }).doOnError(error -> {
            logger.error("SSE stream error for session: " + sessionId, error);
            sseSessionManager.closeSession(sessionId);
        });
    }

    /**
     * HTTP POST endpoint for JSON-RPC requests over SSE transport.
     * ChatGPT Apps sends JSON-RPC requests via POST to the same SSE URL.
     * POST /mcp/sse
     */
    @PostMapping(value = "/sse", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<McpRpcResponse> handleSseJsonRpc(
            @RequestBody McpRpcRequest request,
            @RequestHeader(value = "MCP-Protocol-Version", required = false) String mcpProtocolVersion,
            @RequestHeader(value = "authCode", required = false) String authCode,
            @RequestHeader(value = "partnerClientCode", required = false) String partnerClientCode,
            Authentication authentication) {

        logger.info("Received SSE JSON-RPC request: method={}, id={}, mcpVersion={}",
            request.getMethod(), request.getId(), mcpProtocolVersion);

        try {
            // Process the JSON-RPC request directly (same as HTTP POST /mcp)
            return processJsonRpcRequest(request, authCode, partnerClientCode, authentication);
        } catch (Exception e) {
            logger.error("Error handling SSE JSON-RPC request", e);
            return createErrorResponse(request.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * HTTP POST endpoint for sending JSON-RPC requests to an SSE session.
     * Used in conjunction with SSE endpoint for bidirectional communication.
     * POST /mcp/sse/message
     */
    @PostMapping(value = "/sse/message", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleSseMessage(
            @RequestBody McpRpcRequest request,
            @RequestHeader(value = "X-Session-ID", required = true) String sessionId,
            @RequestHeader(value = "MCP-Protocol-Version", required = false) String mcpProtocolVersion,
            @RequestHeader(value = "authCode", required = false) String authCode,
            @RequestHeader(value = "partnerClientCode", required = false) String partnerClientCode,
            Authentication authentication) {

        logger.info("Received SSE message for session {}: method={}, id={}",
            sessionId, request.getMethod(), request.getId());

        // Validate session exists
        if (!sseSessionManager.hasSession(sessionId)) {
            logger.warn("Session not found: {}", sessionId);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Session not found");
            errorResponse.put("sessionId", sessionId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }

        // Update session activity
        SseSessionManager.SessionInfo sessionInfo = sseSessionManager.getSessionInfo(sessionId);
        if (sessionInfo != null) {
            sessionInfo.updateActivity();
        }

        try {
            // Process the JSON-RPC request using existing logic
            ResponseEntity<McpRpcResponse> response = processJsonRpcRequest(
                request, authCode, partnerClientCode, authentication);

            // Send response back through SSE stream
            if (response.getBody() != null) {
                boolean sent = sseSessionManager.sendToSession(sessionId, response.getBody());
                if (!sent) {
                    logger.error("Failed to send response to session {}", sessionId);
                }
            }

            // Return acknowledgment (actual response goes through SSE)
            Map<String, Object> ackResponse = new HashMap<>();
            ackResponse.put("status", "queued");
            ackResponse.put("sessionId", sessionId);
            ackResponse.put("requestId", request.getId());
            return ResponseEntity.ok(ackResponse);

        } catch (Exception e) {
            logger.error("Error processing SSE message for session " + sessionId, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Processing error");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Extracted JSON-RPC processing logic (used by both HTTP and SSE endpoints).
     */
    private ResponseEntity<McpRpcResponse> processJsonRpcRequest(
            McpRpcRequest request,
            String authCode,
            String partnerClientCode,
            Authentication authentication) {

        // Validate JSON-RPC version
        if (!"2.0".equals(request.getJsonrpc())) {
            return createErrorResponse(request.getId(), -32600, "Invalid JSON-RPC version");
        }

        // Route based on method
        String method = request.getMethod();
        if (method == null) {
            return createErrorResponse(request.getId(), -32600, "Method is required");
        }

        // Handle notification methods (no response required)
        if (method.startsWith("notifications/")) {
            logger.info("Received notification: {}", method);
            return ResponseEntity.ok().build();
        }

        switch (method) {
            case "initialize":
                return handleInitialize(request);
            case "tools/list":
                return handleToolsList(request, authentication);
            case "tools/call":
                return handleToolsCall(request, authCode, partnerClientCode, authentication);
            case "resources/list":
                return handleResourcesList(request);
            case "resources/read":
                return handleResourcesRead(request);
            default:
                return createErrorResponse(request.getId(), -32601, "Method not found: " + method);
        }
    }

    /**
     * Handle initialize method - MCP protocol handshake.
     */
    private ResponseEntity<McpRpcResponse> handleInitialize(McpRpcRequest request) {
        logger.info("Handling initialize request");

        try {
            Map<String, Object> result = new HashMap<>();
            result.put("protocolVersion", protocolVersion);

            // Advertise tools and resources capabilities explicitly
            Map<String, Object> capabilities = new HashMap<>();
            Map<String, Object> toolsCapabilities = new HashMap<>();
            toolsCapabilities.put("list", new HashMap<>());
            toolsCapabilities.put("call", new HashMap<>());
            capabilities.put("tools", toolsCapabilities);

            Map<String, Object> resourcesCapabilities = new HashMap<>();
            resourcesCapabilities.put("list", new HashMap<>());
            resourcesCapabilities.put("read", new HashMap<>());
            capabilities.put("resources", resourcesCapabilities);

            result.put("capabilities", capabilities);

            Map<String, Object> serverInfo = new HashMap<>();
            serverInfo.put("name", serverName);
            serverInfo.put("version", serverVersion);
            result.put("serverInfo", serverInfo);

            McpRpcResponse response = new McpRpcResponse(request.getId(), result);
            return ResponseEntity.ok()
                .header("MCP-Protocol-Version", protocolVersion)
                .body(response);

        } catch (Exception e) {
            logger.error("Error in initialize", e);
            return createErrorResponse(request.getId(), -32603, "Failed to initialize: " + e.getMessage());
        }
    }

    /**
     * Handle tools/list method - returns all available tools filtered by user's scopes.
     * All registered tools are now visible; safety is enforced via scopes and rate limits, not by hiding tools.
     */
    private ResponseEntity<McpRpcResponse> handleToolsList(McpRpcRequest request, Authentication authentication) {
        logger.info("Handling tools/list request");

        try {
            // Debug logging for authentication
            logger.debug("Authentication object: {}", authentication);
            if (authentication != null) {
                logger.debug("Authentication class: {}", authentication.getClass().getName());
                logger.debug("Authentication principal: {}", authentication.getPrincipal());
                logger.debug("Authentication authorities: {}", authentication.getAuthorities());
            }

            // Check rate limit for read operations
            if (!rateLimitService.allowRead()) {
                return createErrorResponse(request.getId(), -32000, "Rate limit exceeded for read operations");
            }

            List<McpToolDefinition> allTools = toolRegistry.getAllToolDefinitions();
            logger.debug("Total tools available: {}", allTools.size());

            if (allTools.isEmpty()) {
                logger.warn("No tool definitions loaded – check that schemas/*.json exist and load correctly");
            }

            // Expose all tools via tools/list; clients use safety metadata + scopes to decide how to call them.
            List<McpToolDefinition> filteredTools = allTools;

            logger.debug("Filtered tools count: {}", filteredTools.size());

            // Categorize tools by safety
            Map<String, List<McpToolDefinition>> categorizedTools = new HashMap<>();
            List<McpToolDefinition> readOnlyTools = filteredTools.stream()
                .filter(t -> Boolean.TRUE.equals(t.getReadOnly()))
                .collect(Collectors.toList());
            List<McpToolDefinition> writeTools = filteredTools.stream()
                .filter(t -> !Boolean.TRUE.equals(t.getReadOnly()))
                .collect(Collectors.toList());

            categorizedTools.put("readOnly", readOnlyTools);
            categorizedTools.put("write", writeTools);

            Map<String, Object> result = new HashMap<>();
            result.put("tools", filteredTools);
            result.put("categorized", categorizedTools);
            result.put("totalCount", filteredTools.size());

            McpRpcResponse response = new McpRpcResponse(request.getId(), result);
            return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore().mustRevalidate())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(response);

        } catch (Exception e) {
            logger.error("Error listing tools", e);
            return createErrorResponse(request.getId(), -32603, "Failed to list tools: " + e.getMessage());
        }
    }

    /**
     * Handle resources/list - list large execution results exposed as MCP resources (searchable context, no context rot).
     * No caching: responses are always fresh.
     */
    private ResponseEntity<McpRpcResponse> handleResourcesList(McpRpcRequest request) {
        logger.info("Handling resources/list request");
        try {
            List<Map<String, Object>> resources = actionListService.listResultResources();
            Map<String, Object> result = new HashMap<>();
            result.put("resources", resources);
            McpRpcResponse response = new McpRpcResponse(request.getId(), result);
            return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore().mustRevalidate())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(response);
        } catch (Exception e) {
            logger.error("Error listing resources", e);
            return createErrorResponse(request.getId(), -32603, "Failed to list resources: " + e.getMessage());
        }
    }

    /**
     * Handle resources/read - read a large result resource by URI. Resource remains until release_resource is called.
     */
    private ResponseEntity<McpRpcResponse> handleResourcesRead(McpRpcRequest request) {
        logger.info("Handling resources/read request");
        try {
            Map<String, Object> params = request.getParams();
            String uri = params != null ? (String) params.get("uri") : null;
            if (uri == null || uri.trim().isEmpty()) {
                return createErrorResponse(request.getId(), -32602, "Params.uri is required");
            }
            String content = actionListService.readResultResource(uri.trim());
            if (content == null) {
                return createErrorResponse(request.getId(), -32602, "Resource not found or already released: " + uri);
            }
            Map<String, Object> contentItem = new HashMap<>();
            contentItem.put("uri", uri);
            contentItem.put("mimeType", "application/json");
            contentItem.put("text", content);
            Map<String, Object> result = new HashMap<>();
            result.put("contents", Collections.singletonList(contentItem));
            McpRpcResponse response = new McpRpcResponse(request.getId(), result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error reading resource", e);
            return createErrorResponse(request.getId(), -32603, "Failed to read resource: " + e.getMessage());
        }
    }

    /**
     * Handle tools/call method - executes a specific tool with scope enforcement.
     * All registered tools may now be called directly (subject to scopes and rate limits).
     */
    private ResponseEntity<McpRpcResponse> handleToolsCall(
            McpRpcRequest request, String authCode, String partnerClientCode, Authentication authentication) {

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

            // Check if tool exists
            if (!toolRegistry.hasTool(toolName)) {
                auditLogService.logFailure(toolName, arguments, "Tool not found");
                return createErrorResponse(request.getId(), -32602, "Unknown tool: " + toolName);
            }

            // Get tool definition to check safety metadata
            McpToolDefinition toolDef = toolRegistry.getToolDefinition(toolName);

            // Check scope authorization
            Set<String> userScopes = extractScopes(authentication);
            if (!hasRequiredScope(toolDef, userScopes)) {
                auditLogService.logFailure(toolName, arguments, "Insufficient scope: required " + toolDef.getRequiredScope());
                return createErrorResponse(request.getId(), -32000,
                    "Insufficient permissions. Required scope: " + toolDef.getRequiredScope());
            }

            // Check rate limit based on tool type
            boolean rateLimitOk = Boolean.TRUE.equals(toolDef.getReadOnly()) ?
                rateLimitService.allowRead() : rateLimitService.allowWrite();

            if (!rateLimitOk) {
                String limitType = Boolean.TRUE.equals(toolDef.getReadOnly()) ? "read" : "write";
                auditLogService.logFailure(toolName, arguments, "Rate limit exceeded: " + limitType);

                // Return a cooldown-style MCP result instead of a JSON-RPC error so
                // clients see the connector as healthy but temporarily rate-limited.
                List<Map<String, Object>> contentArray = new ArrayList<>();
                Map<String, Object> contentItem = new HashMap<>();
                contentItem.put("type", "text");
                contentItem.put("text",
                    "This connector has reached its " + limitType + " rate limit. " +
                    "Please wait about 60 seconds before making more " + limitType + " tool calls.");
                contentArray.add(contentItem);

                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("content", contentArray);
                resultMap.put("isError", true);
                resultMap.put("rateLimited", true);
                resultMap.put("limitType", limitType);

                McpRpcResponse response = new McpRpcResponse(request.getId(), resultMap);
                return ResponseEntity.ok(response);
            }

            // Extract authCode and partnerClientCode from JWT if in OAuth2 mode
            String finalAuthCode = authCode;
            String finalPartnerCode = partnerClientCode;

            if (authentication instanceof JwtAuthenticationToken && jwtClaimsExtractor != null) {
                JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
                Jwt jwt = jwtAuth.getToken();

                // Always extract client's own authCode from JWT (from client_contact_details table)
                String jwtAuthCode = jwtClaimsExtractor.extractAuthCode(jwt);

                // Check if partnerClientCode is provided in tool arguments (ChatGPT Apps can't pass headers)
                String toolPartnerCode = arguments != null ? (String) arguments.get("partnerClientCode") : null;
                if (toolPartnerCode != null && !toolPartnerCode.isEmpty()) {
                    finalPartnerCode = toolPartnerCode;
                    logger.info("Extracted partnerClientCode from tool arguments: {}", toolPartnerCode);
                }

                // If partnerClientCode is provided (from tool arguments or header),
                // use partner_auth_code instead of regular auth_code
                if (finalPartnerCode != null && !finalPartnerCode.isEmpty()) {
                    // Client wants to act on behalf of office/sub-client
                    logger.info("Client requesting tags for partnerClientCode: {}", finalPartnerCode);

                    // Use partner_auth_code from JWT (from partner table)
                    String partnerAuthCode = jwtClaimsExtractor.extractPartnerAuthCode(jwt);
                    if (partnerAuthCode != null && !partnerAuthCode.isEmpty()) {
                        finalAuthCode = partnerAuthCode;
                        logger.info("Using partner_auth_code for partnerClientCode: {}", finalPartnerCode);
                    } else {
                        logger.warn("partnerClientCode provided but partner_auth_code not found in JWT");
                        return createErrorResponse(request.getId(), -32001,
                                "Partner authentication is not available. Your account cannot access tags for partner offices. " +
                                "Please contact support to enable partner access.");
                    }
                } else {
                    // Normal case: client calling APIs for themselves
                    finalAuthCode = jwtAuthCode;
                    logger.debug("Using auth_code from JWT claims (client's own auth)");
                }
            }

            // Validate auth credentials for backend API calls
            if (finalAuthCode == null || finalAuthCode.isEmpty()) {
                auditLogService.logFailure(toolName, arguments, "Missing authCode");
                return createErrorResponse(request.getId(), -32001,
                    "authCode is required in JWT claims (must be added by OAuth2 server)");
            }

            // Execute the tool with credentials from JWT or headers
            Object result = toolRegistry.execute(toolName, arguments, finalAuthCode, finalPartnerCode);

            // Log successful execution
            auditLogService.logSuccess(toolName, arguments);

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

            // Extract the actual data from the API response
            Object dataToReturn = parsedResult;
            if (parsedResult instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) parsedResult;
                if (responseMap.containsKey("data")) {
                    dataToReturn = responseMap.get("data");
                }
            }

            // Wrap large results as MCP resources when necessary (applies to all tools)
            Object maybeWrapped = actionListService.wrapLargeResultIfNeeded(dataToReturn);

            // Format response according to MCP protocol
            List<Map<String, Object>> contentArray = new ArrayList<>();
            Map<String, Object> contentItem = new HashMap<>();
            contentItem.put("type", "text");
            contentItem.put("text", objectMapper.writeValueAsString(maybeWrapped));
            contentArray.add(contentItem);

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("content", contentArray);
            resultMap.put("isError", false);

            McpRpcResponse response = new McpRpcResponse(request.getId(), resultMap);
            return ResponseEntity.ok(response);

        } catch (org.everit.json.schema.ValidationException e) {
            logger.error("Validation error", e);
            auditLogService.logFailure((String) request.getParams().get("name"),
                (Map<String, Object>) request.getParams().get("arguments"), "Validation error: " + e.getMessage());
            return createErrorResponse(request.getId(), -32602, "Invalid arguments: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument", e);
            auditLogService.logFailure((String) request.getParams().get("name"),
                (Map<String, Object>) request.getParams().get("arguments"), e.getMessage());
            return createErrorResponse(request.getId(), -32602, e.getMessage());
        } catch (Exception e) {
            logger.error("Error executing tool", e);
            auditLogService.logFailure((String) request.getParams().get("name"),
                (Map<String, Object>) request.getParams().get("arguments"), "Execution error: " + e.getMessage());
            return createErrorResponse(request.getId(), -32603, "Tool execution failed: " + e.getMessage());
        }
    }

    /**
     * Extract OAuth2 scopes from authentication.
     */
    private Set<String> extractScopes(Authentication authentication) {
        if (authentication == null) {
            return Collections.emptySet();
        }

        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(auth -> auth.startsWith("SCOPE_"))
            .map(auth -> auth.substring(6)) // Remove "SCOPE_" prefix
            .collect(Collectors.toSet());
    }

    /**
     * Check if user has required scope for the tool.
     */
    private boolean hasRequiredScope(McpToolDefinition tool, Set<String> userScopes) {
        String requiredScope = tool.getRequiredScope();
        if (requiredScope == null || requiredScope.isEmpty()) {
            // If no scope required, allow access
            return true;
        }

        return userScopes.contains(requiredScope);
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
     * Health check endpoint (now redundant with actuator, but kept for backwards compatibility).
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", serverName);
        health.put("version", serverVersion);
        health.put("protocolVersion", protocolVersion);
        health.put("toolsRegistered", toolRegistry.getAllToolDefinitions().size());
        return ResponseEntity.ok(health);
    }
}
