package com.textellent.mcp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textellent.mcp.registry.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for the batch action system.
 * Plans are validated and executed in a single batch_action call (stateless execution).
 */
@Service
public class ActionListService {

    private static final Logger logger = LoggerFactory.getLogger(ActionListService.class);

    /** Large result resources: key -> JSON string. Exposed as MCP resources (textellent://result/{key}). Kept until the model calls release_resource. */
    private final Map<String, String> resultResourceStore = new ConcurrentHashMap<>();

    /** URI prefix for large execution results exposed as MCP resources. */
    public static final String RESULT_RESOURCE_URI_PREFIX = "textellent://result/";

    private static final int TOKEN_ESTIMATE_CHARS_PER_TOKEN = 3;
    private static final int LARGE_RESULT_TOKEN_THRESHOLD = 32_000;

    @Lazy
    @Autowired
    private McpToolRegistry toolRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * batch_action: validate and execute a proposed action plan in a single call.
     * Any registered tool is allowed. Returns aggregated step results or errors.
     */
    @SuppressWarnings("unchecked")
    public Object setActionList(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("ActionListService.setActionList (batch_action) called with arguments: {}", arguments);

        List<String> errors = new ArrayList<>();
        Object actionsObj = arguments != null ? arguments.get("actions") : null;
        Object initialInputObj = arguments != null ? arguments.get("initialInput") : null;

        if (actionsObj == null || !(actionsObj instanceof List)) {
            Map<String, Object> fail = new HashMap<>();
            fail.put("valid", false);
            fail.put("errors", Collections.singletonList("Missing or invalid 'actions' array."));
            return fail;
        }

        List<Map<String, Object>> actions = (List<Map<String, Object>>) actionsObj;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < actions.size(); i++) {
            Map<String, Object> action = actions.get(i);
            if (!(action instanceof Map)) {
                errors.add("Action at index " + i + " must be an object.");
                continue;
            }
            String id = (String) action.get("id");
            String tool = (String) action.get("tool");
            if (id == null || id.trim().isEmpty()) {
                errors.add("Action at index " + i + " missing 'id'.");
            } else if (ids.contains(id)) {
                errors.add("Duplicate action id: " + id);
            } else {
                ids.add(id);
            }
            if (tool == null || tool.trim().isEmpty()) {
                errors.add("Action at index " + i + " (id=" + (id != null ? id : "?") + ") missing 'tool'.");
            } else if (!toolRegistry.hasTool(tool)) {
                errors.add("Action at index " + i + ": unknown tool '" + tool + "'.");
            }
        }

        if (!errors.isEmpty()) {
            Map<String, Object> fail = new HashMap<>();
            fail.put("success", false);
            fail.put("errors", errors);
            return fail;
        }

        Map<String, Object> initialInput = initialInputObj instanceof Map
                ? (Map<String, Object>) initialInputObj
                : Collections.emptyMap();

        Map<String, Object> plan = new HashMap<>();
        plan.put("initialInput", initialInput);
        plan.put("actions", actions);

        try {
            String planJson = objectMapper.writeValueAsString(plan);
            logger.info("Executing action plan via batch_action. plan={}", planJson);
        } catch (Exception e) {
            logger.warn("Failed to serialize action plan for logging: {}", e.getMessage());
        }

        Object rawResult = runPlanFromIndex(
                plan,
                initialInput,
                actions,
                0,
                new HashMap<>(),
                new ArrayList<>(),
                authCode,
                partnerClientCode
        );

        return wrapLargeResultIfNeeded(rawResult);
    }

    /**
     * If result JSON exceeds token threshold (~32K), store as one-time MCP resource and return wrapper with resource URI.
     * The model can explore this resource without loading it into context (searchable context, no context rot).
     */
    public Object wrapLargeResultIfNeeded(Object rawResult) {
        try {
            String resultJson = objectMapper.writeValueAsString(rawResult);
            int tokenEstimate = resultJson.length() / TOKEN_ESTIMATE_CHARS_PER_TOKEN;
            if (tokenEstimate > LARGE_RESULT_TOKEN_THRESHOLD) {
                String resultKey = UUID.randomUUID().toString();
                resultResourceStore.put(resultKey, resultJson);
                String uri = RESULT_RESOURCE_URI_PREFIX + resultKey;
                Map<String, Object> wrapper = new HashMap<>();
                wrapper.put("success", true);
                wrapper.put("resultTokenCount", tokenEstimate);
                wrapper.put("resultTooLarge", true);
                wrapper.put("resultResourceUri", uri);
                wrapper.put("message", "Result exceeds 32K tokens. It is available as a resource at " + uri + ". You can explore or search this resource without loading the full content into context. When you are done using it, call the release_resource tool with this URI (or the resource identifier " + resultKey + ") to release the resource on the server.");
                return wrapper;
            }
        } catch (Exception e) {
            logger.warn("Could not serialize result for token check", e);
        }
        return rawResult;
    }

    /**
     * List URIs of currently stored large-result resources (for MCP resources/list).
     */
    public List<Map<String, Object>> listResultResources() {
        List<Map<String, Object>> resources = new ArrayList<>();
        for (String key : resultResourceStore.keySet()) {
            Map<String, Object> r = new HashMap<>();
            r.put("uri", RESULT_RESOURCE_URI_PREFIX + key);
            r.put("name", "Execution result");
            r.put("description", "Large execution result; explore or search without loading into context. Call release_resource with this URI when done.");
            r.put("mimeType", "application/json");
            resources.add(r);
        }
        return resources;
    }

    /**
     * Read a large-result resource by URI (for MCP resources/read). Does not remove; use release_resource when done.
     */
    public String readResultResource(String uri) {
        String key = uriToResultKey(uri);
        return key != null ? resultResourceStore.get(key) : null;
    }

    /**
     * Release a large-result resource by URI or key. Call when the model is done using the resource. Removes it from the server.
     */
    public Object releaseResource(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        String identifier = arguments != null ? (String) arguments.get("resourceIdentifier") : null;
        if (identifier == null || identifier.trim().isEmpty()) {
            Map<String, Object> fail = new HashMap<>();
            fail.put("released", false);
            fail.put("error", "Missing 'resourceIdentifier'. Provide the resultResourceUri (e.g. textellent://result/{id}) or the resource id returned when resultTooLarge is true.");
            return fail;
        }
        String key = uriToResultKey(identifier.trim());
        if (key == null) {
            key = identifier.trim();
        }
        String removed = resultResourceStore.remove(key);
        if (removed == null) {
            Map<String, Object> fail = new HashMap<>();
            fail.put("released", false);
            fail.put("error", "Resource not found or already released: " + identifier);
            return fail;
        }
        Map<String, Object> ok = new HashMap<>();
        ok.put("released", true);
        ok.put("message", "Resource released.");
        return ok;
    }

    private String uriToResultKey(String uri) {
        if (uri == null || !uri.startsWith(RESULT_RESOURCE_URI_PREFIX)) {
            return null;
        }
        return uri.substring(RESULT_RESOURCE_URI_PREFIX.length()).trim();
    }

    /**
     * Run plan actions from startIndex until end. Returns final aggregated result.
     */
    @SuppressWarnings("unchecked")
    private Object runPlanFromIndex(Map<String, Object> plan, Map<String, Object> initialInput,
                                   List<Map<String, Object>> actions, int startIndex,
                                   Map<String, Object> context, List<Map<String, Object>> resultsSoFar,
                                   String authCode, String partnerClientCode) {
        Map<String, Object> contextLocal = new HashMap<>(context);
        List<Map<String, Object>> results = new ArrayList<>(resultsSoFar);
        boolean allSuccess = true;

        for (int i = startIndex; i < actions.size(); i++) {
            Map<String, Object> action = actions.get(i);
            String actionId = (String) action.get("id");
            String tool = (String) action.get("tool");
            Map<String, Object> args = action.get("arguments") instanceof Map ? (Map<String, Object>) action.get("arguments") : new HashMap<>();
            Map<String, Object> inputsFrom = action.get("inputsFrom") instanceof Map ? (Map<String, Object>) action.get("inputsFrom") : null;

            Map<String, Object> resolvedArgs = new HashMap<>(args);
            if (inputsFrom != null) {
                for (Map.Entry<String, Object> e : inputsFrom.entrySet()) {
                    Object ref = e.getValue();
                    if (ref instanceof String) {
                        Object value = resolveReference((String) ref, initialInput, contextLocal);
                        if (value != null) {
                            resolvedArgs.put(e.getKey(), value);
                        }
                    }
                }
            }

            Map<String, Object> stepResult = new HashMap<>();
            stepResult.put("actionId", actionId);
            stepResult.put("tool", tool);
            try {
                Object output = toolRegistry.execute(tool, resolvedArgs, authCode, partnerClientCode);
                if (output instanceof String) {
                    try {
                        output = objectMapper.readValue((String) output, Object.class);
                    } catch (Exception ignored) {
                        // leave as string
                    }
                }
                stepResult.put("status", "success");
                stepResult.put("output", output != null ? output : Collections.emptyMap());
                if (actionId != null) {
                    contextLocal.put(actionId, output);
                }
            } catch (Exception ex) {
                logger.warn("Action {} ({}): {}", actionId, tool, ex.getMessage());
                stepResult.put("status", "error");
                stepResult.put("error", ex.getMessage());
                stepResult.put("output", null);
                allSuccess = false;
            }
            results.add(stepResult);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", allSuccess);
        result.put("results", results);
        return result;
    }

    /**
     * Resolve a reference like $prev.step1.data.id or $initial.contactId.
     */
    @SuppressWarnings("unchecked")
    private Object resolveReference(String ref, Map<String, Object> initialInput, Map<String, Object> context) {
        if (ref == null || !ref.startsWith("$")) return null;
        String path = ref.substring(1).trim();
        Map<String, Object> root;
        if (path.startsWith("initial.")) {
            root = initialInput;
            path = path.substring("initial.".length());
        } else if (path.startsWith("prev.")) {
            path = path.substring("prev.".length());
            int dot = path.indexOf('.');
            String stepId = dot > 0 ? path.substring(0, dot) : path;
            path = dot > 0 ? path.substring(dot + 1) : "";
            Object stepOut = context.get(stepId);
            if (stepOut instanceof Map) {
                root = (Map<String, Object>) stepOut;
            } else {
                return stepOut;
            }
        } else {
            return null;
        }
        if (path.isEmpty()) return root;
        String[] keys = path.split("\\.");
        Object current = root;
        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(key);
            } else {
                return null;
            }
        }
        return current;
    }
}

