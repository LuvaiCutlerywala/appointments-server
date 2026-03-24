package com.textellent.mcp.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.textellent.mcp.models.McpToolDefinition;
import com.textellent.mcp.services.AppointmentApiService;
import com.textellent.mcp.services.CallbackEventApiService;
import com.textellent.mcp.services.DslOrchestrationService;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Registry for MCP tools that maps tool names to their handlers and definitions.
 */
@Component
public class McpToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(McpToolRegistry.class);

    private static final String DSL_STEP_ONLY_TOOL_DESCRIPTION_PREFIX =
        "MCP invocation policy (required): Do not call this tool with MCP tools/call. "
            + "It appears in tools/list only for discovery (this tool's name and inputSchema). "
            + "Run it only as a step inside a plan submitted to dsl_execute_plan (use this tool's name as the step `tool` field).\n\n";

    private static final String DIRECT_ORCHESTRATOR_TOOL_DESCRIPTION_PREFIX =
        "MCP invocation policy (required): This is the only tool you may invoke via MCP tools/call. "
            + "Every other tool in tools/list is dsl_step_only: use those names only inside the plan you pass here.\n\n";

    private static final String INPUT_SCHEMA_DSL_STEP_NOTE =
        "MCP (required): These properties are step arguments inside dsl_execute_plan only—not a standalone tools/call payload.";

    private static final String INPUT_SCHEMA_DIRECT_ORCHESTRATOR_NOTE =
        "MCP (required): This object is the arguments map for tools/call when the tool name is dsl_execute_plan.";

    private static final String OUTPUT_SCHEMA_DSL_STEP_NOTE =
        "MCP: Response shape when this primitive is executed as a dsl_execute_plan step (never from tools/call).";

    private final Map<String, McpToolHandler> handlers = new HashMap<>();
    private final Map<String, McpToolDefinition> toolDefinitions = new HashMap<>();
    private final Map<String, Schema> schemas = new HashMap<>();

    @Autowired
    private DslOrchestrationService dslOrchestrationService;

    @Autowired
    private AppointmentApiService appointmentApiService;

    @Autowired
    private CallbackEventApiService callbackEventApiService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        registerAllTools();
        loadToolSchemas();
    }

    private void registerAllTools() {
        registerTool("dsl_execute_plan", dslOrchestrationService::executePlan);

        registerTool("appointments_create", appointmentApiService::createAppointment);
        registerTool("appointments_update", appointmentApiService::updateAppointment);
        registerTool("appointments_cancel", appointmentApiService::cancelAppointment);

        registerTool("events_appointment_created", (args, ac, pc) ->
                callbackEventApiService.fetchPagedEvents("/api/v1/events/appointmentCreated.json", args, ac, pc));
        registerTool("events_appointment_updated", (args, ac, pc) ->
                callbackEventApiService.fetchPagedEvents("/api/v1/events/appointmentUpdated.json", args, ac, pc));
        registerTool("events_appointment_canceled", (args, ac, pc) ->
                callbackEventApiService.fetchPagedEvents("/api/v1/events/appointmentCanceled.json", args, ac, pc));

        logger.info("Registered {} MCP tools", handlers.size());
    }

    private void registerTool(String toolName, McpToolHandler handler) {
        handlers.put(toolName, handler);
    }

    @SuppressWarnings("unchecked")
    private void loadToolSchemas() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:schemas/*.json");

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    String filename = resource.getFilename();
                    if (filename != null) {
                        String toolName = filename.replace(".json", "");

                        Map<String, Object> schemaMap = objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});

                        Map<String, Object> inputSchema = (Map<String, Object>) schemaMap.get("inputSchema");
                        Map<String, Object> outputSchema = (Map<String, Object>) schemaMap.get("outputSchema");

                        boolean mustBeDirect = "dsl_execute_plan".equals(toolName);
                        Map<String, Object> xTextellentMcp = resolveTextellentMcpExtension(toolName, schemaMap, mustBeDirect);

                        boolean directToolsCall = Boolean.TRUE.equals(xTextellentMcp.get("directToolsCall"));
                        if (directToolsCall) {
                            prependRootSchemaDescription(inputSchema, INPUT_SCHEMA_DIRECT_ORCHESTRATOR_NOTE);
                        } else {
                            prependRootSchemaDescription(inputSchema, INPUT_SCHEMA_DSL_STEP_NOTE);
                            prependRootSchemaDescription(outputSchema, OUTPUT_SCHEMA_DSL_STEP_NOTE);
                        }

                        String baseDescription = (String) schemaMap.get("description");
                        String fullDescription = (directToolsCall ? DIRECT_ORCHESTRATOR_TOOL_DESCRIPTION_PREFIX : DSL_STEP_ONLY_TOOL_DESCRIPTION_PREFIX)
                            + (baseDescription != null ? baseDescription : "");

                        McpToolDefinition toolDef = new McpToolDefinition();
                        toolDef.setName(toolName);
                        toolDef.setDescription(fullDescription);
                        toolDef.setInputSchema(inputSchema);
                        toolDef.setOutputSchema(outputSchema);
                        toolDef.setTextellentMcp(xTextellentMcp);

                        configureSafetyMetadata(toolName, toolDef);

                        toolDefinitions.put(toolName, toolDef);

                        if (inputSchema != null) {
                            JSONObject jsonSchema = new JSONObject(inputSchema);
                            Schema schema = SchemaLoader.load(jsonSchema);
                            schemas.put(toolName, schema);
                        }

                        logger.debug("Loaded schema for tool: {}", toolName);
                    }
                } catch (Exception e) {
                    logger.error("Failed to load schema from resource: {}", resource.getFilename(), e);
                }
            }

            logger.info("Loaded {} tool schemas", toolDefinitions.size());
        } catch (IOException e) {
            logger.error("Failed to load tool schemas", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveTextellentMcpExtension(String toolName, Map<String, Object> schemaMap, boolean mustBeDirect) {
        Map<String, Object> xtm = (Map<String, Object>) schemaMap.get("x-textellent-mcp");
        if (xtm == null) {
            logger.warn("Schema file for tool '{}' is missing x-textellent-mcp; add it to the JSON schema for explicit MCP policy.", toolName);
            xtm = new LinkedHashMap<>();
        } else {
            xtm = new LinkedHashMap<>(xtm);
        }

        Object rawDirect = xtm.get("directToolsCall");
        boolean directToolsCall;
        if (rawDirect instanceof Boolean) {
            directToolsCall = (Boolean) rawDirect;
        } else {
            directToolsCall = mustBeDirect;
            if (rawDirect != null) {
                logger.warn("Tool '{}' x-textellent-mcp.directToolsCall must be a boolean; coercing to {}", toolName, mustBeDirect);
            }
        }

        if (directToolsCall != mustBeDirect) {
            logger.warn("Tool '{}' x-textellent-mcp.directToolsCall={} conflicts with server policy (expected {}); enforcing policy.",
                toolName, directToolsCall, mustBeDirect);
            directToolsCall = mustBeDirect;
        }

        xtm.put("directToolsCall", directToolsCall);
        xtm.put("invocation", directToolsCall ? "direct_tools_call" : "dsl_step_only");
        return Collections.unmodifiableMap(xtm);
    }

    private void prependRootSchemaDescription(Map<String, Object> schemaRoot, String mcpNote) {
        if (schemaRoot == null || mcpNote == null) {
            return;
        }
        Object existing = schemaRoot.get("description");
        if (existing instanceof String) {
            String str = (String) existing;
            if (!str.isEmpty()) {
                if (str.startsWith(mcpNote)) {
                    return;
                }
                schemaRoot.put("description", mcpNote + " " + str);
                return;
            }
        }
        schemaRoot.put("description", mcpNote);
    }

    public List<McpToolDefinition> getAllToolDefinitions() {
        return new ArrayList<>(toolDefinitions.values());
    }

    public Object execute(String toolName, Map<String, Object> arguments, String authCode, String partnerClientCode) throws Exception {
        McpToolHandler handler = handlers.get(toolName);
        if (handler == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        validateArguments(toolName, arguments);

        return handler.execute(arguments, authCode, partnerClientCode);
    }

    public boolean hasTool(String toolName) {
        return handlers.containsKey(toolName);
    }

    public McpToolDefinition getToolDefinition(String toolName) {
        return toolDefinitions.get(toolName);
    }

    private void validateArguments(String toolName, Map<String, Object> arguments) throws ValidationException {
        Schema schema = schemas.get(toolName);
        if (schema != null && arguments != null) {
            JSONObject jsonObject = new JSONObject(arguments);
            schema.validate(jsonObject);
        }
    }

    private void configureSafetyMetadata(String toolName, McpToolDefinition toolDef) {
        if (toolName.startsWith("events_")) {
            toolDef.setReadOnly(true);
            toolDef.setDestructive(false);
            toolDef.setRequiredScope("read");
        } else if (toolName.contains("_delete") || toolName.contains("_cancel") || toolName.contains("_update")) {
            toolDef.setReadOnly(false);
            toolDef.setDestructive(true);
            toolDef.setRequiredScope("write");
        } else {
            toolDef.setReadOnly(false);
            toolDef.setDestructive(false);
            toolDef.setRequiredScope("write");
        }

        if ("dsl_execute_plan".equals(toolName)) {
            toolDef.setRequiredScope(null);
        }
    }
}
