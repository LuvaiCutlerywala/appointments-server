package com.textellent.mcp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.textellent.mcp.models.McpToolDefinition;
import com.textellent.mcp.registry.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * Service for the Action List / commit system.
 * Transaction-only tools are discoverable via tools/list but must be used via:
 * action_list (validate plan) → user approval → execute (commit).
 * list_actions returns the catalog of tools that can appear in a plan.
 * Supports model-in-the-loop: model_evaluation steps pause execution; execute_continue resumes.
 */
@Service
public class ActionListService {

    private static final Logger logger = LoggerFactory.getLogger(ActionListService.class);

    /** Special tool: pauses execution and returns control to the model with state. */
    public static final String MODEL_EVALUATION = "model_evaluation";

    /** Paused executions: executionId -> state. Cleared when execution completes or times out. */
    private final Map<String, ExecutionState> executionStates = new ConcurrentHashMap<>();

    /** Cached plans: planId -> plan (initialInput + actions). Used when execute is called with planId. */
    private final Map<String, Map<String, Object>> planCache = new ConcurrentHashMap<>();

    /** Large result resources: key -> JSON string. Exposed as MCP resources (textellent://result/{key}). Kept until the model calls release_resource. */
    private final Map<String, String> resultResourceStore = new ConcurrentHashMap<>();

    /** URI prefix for large execution results exposed as MCP resources. */
    public static final String RESULT_RESOURCE_URI_PREFIX = "textellent://result/";

    private static final long EXECUTION_STATE_TTL_MS = 30 * 60 * 1000; // 30 minutes
    private static final int TOKEN_ESTIMATE_CHARS_PER_TOKEN = 3;
    private static final int LARGE_RESULT_TOKEN_THRESHOLD = 32_000;

    @Lazy
    @Autowired
    private McpToolRegistry toolRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * list_actions: return catalog of all transaction-only tools that can appear in a plan.
     */
    public Object listActions(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("ActionListService.listActions called");
        List<Map<String, Object>> actions = new ArrayList<>();
        for (McpToolDefinition def : toolRegistry.getTransactionOnlyToolDefinitions()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("name", def.getName());
            entry.put("description", def.getDescription());
            entry.put("inputSchema", def.getInputSchema());
            entry.put("annotations", def.getAnnotations());
            actions.add(entry);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("actions", actions);
        return result;
    }

    /**
     * action_list: validate a proposed action plan. Only transaction-only tools (and model_evaluation) are allowed.
     * Returns validated plan or errors. No side effects.
     */
    @SuppressWarnings("unchecked")
    public Object setActionList(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("ActionListService.setActionList (action_list) called with arguments: {}", arguments);

        Set<String> allowedTools = toolRegistry.getTransactionOnlyToolNames();
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
            } else if (!MODEL_EVALUATION.equals(tool) && !allowedTools.contains(tool)) {
                errors.add("Action at index " + i + ": tool '" + tool + "' is not allowed (transaction-only tools only).");
            } else if (!MODEL_EVALUATION.equals(tool) && !toolRegistry.hasTool(tool)) {
                errors.add("Action at index " + i + ": unknown tool '" + tool + "'.");
            }
        }

        if (!errors.isEmpty()) {
            Map<String, Object> fail = new HashMap<>();
            fail.put("valid", false);
            fail.put("errors", errors);
            return fail;
        }

        Map<String, Object> plan = new HashMap<>();
        plan.put("initialInput", initialInputObj != null && initialInputObj instanceof Map ? initialInputObj : Collections.emptyMap());
        plan.put("actions", actions);

        String planId = UUID.randomUUID().toString();
        planCache.put(planId, plan);

        Map<String, Object> result = new HashMap<>();
        result.put("valid", true);
        result.put("planId", planId);
        result.put("presentation", buildPresentation(planId, plan, actions));
        return result;
    }

    /**
     * execute: run a user-approved plan by planId (from action_list). Resolves inputsFrom and runs each action in order.
     * Only transaction-only tools (and model_evaluation) are executed.
     * If result JSON exceeds token threshold (~32K tokens), stores result as an MCP resource and returns its URI so the model can explore it without context rot.
     */
    @SuppressWarnings("unchecked")
    public Object executePlan(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("ActionListService.executePlan called");

        Map<String, Object> plan = null;
        String planId = arguments != null ? (String) arguments.get("planId") : null;
        if (planId != null && !planId.trim().isEmpty()) {
            plan = planCache.get(planId.trim());
            if (plan == null) {
                Map<String, Object> fail = new HashMap<>();
                fail.put("success", false);
                Map<String, Object> errEntry = new HashMap<>();
                errEntry.put("error", "Plan not found or expired for planId: " + planId);
                fail.put("results", Collections.singletonList(errEntry));
                return fail;
            }
        }
        if (plan == null) {
            Object planObj = arguments != null ? arguments.get("plan") : null;
            if (planObj == null || !(planObj instanceof Map)) {
                Map<String, Object> fail = new HashMap<>();
                fail.put("success", false);
                Map<String, Object> errEntry = new HashMap<>();
                errEntry.put("error", "Provide 'planId' (from action_list) or 'plan' object with initialInput and actions.");
                fail.put("results", Collections.singletonList(errEntry));
                return fail;
            }
            plan = (Map<String, Object>) planObj;
        }

        // Log the approved plan that is about to be executed so operators can see all planned steps.
        try {
            String planJson = objectMapper.writeValueAsString(plan);
            if (planId != null && !planId.trim().isEmpty()) {
                logger.info("Executing approved action plan. planId={}, plan={}", planId, planJson);
            } else {
                logger.info("Executing approved inline action plan (no planId). plan={}", planJson);
            }
        } catch (Exception e) {
            logger.warn("Failed to serialize action plan for logging: {}", e.getMessage());
        }

        Object actionsObj = plan.get("actions");
        Object initialInputObj = plan.get("initialInput");
        Map<String, Object> initialInput = initialInputObj instanceof Map ? (Map<String, Object>) initialInputObj : new HashMap<>();
        if (actionsObj == null || !(actionsObj instanceof List)) {
            Map<String, Object> fail = new HashMap<>();
            fail.put("success", false);
            Map<String, Object> errEntry2 = new HashMap<>();
            errEntry2.put("error", "Plan must contain 'actions' array.");
            fail.put("results", Collections.singletonList(errEntry2));
            return fail;
        }

        List<Map<String, Object>> actions = (List<Map<String, Object>>) actionsObj;
        Object rawResult = runPlanFromIndex(plan, initialInput, actions, 0, new HashMap<>(), new ArrayList<>(), authCode, partnerClientCode, null);

        // If paused, return as-is
        if (rawResult instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) rawResult).get("paused"))) {
            return rawResult;
        }
        return wrapLargeResultIfNeeded(rawResult);
    }

    /**
     * If result JSON exceeds token threshold (~32K), store as one-time MCP resource and return wrapper with resource URI.
     * The model can explore this resource without loading it into context (searchable context, no context rot).
     */
    private Object wrapLargeResultIfNeeded(Object rawResult) {
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
     * Run plan actions from startIndex until end or next model_evaluation. Returns final result or paused result.
     */
    @SuppressWarnings("unchecked")
    private Object runPlanFromIndex(Map<String, Object> plan, Map<String, Object> initialInput,
                                   List<Map<String, Object>> actions, int startIndex,
                                   Map<String, Object> context, List<Map<String, Object>> resultsSoFar,
                                   String authCode, String partnerClientCode, String existingExecutionId) {
        Map<String, Object> contextLocal = new HashMap<>(context);
        List<Map<String, Object>> results = new ArrayList<>(resultsSoFar);
        boolean allSuccess = true;

        for (int i = startIndex; i < actions.size(); i++) {
            Map<String, Object> action = actions.get(i);
            String actionId = (String) action.get("id");
            String tool = (String) action.get("tool");
            Map<String, Object> args = action.get("arguments") instanceof Map ? (Map<String, Object>) action.get("arguments") : new HashMap<>();
            Map<String, Object> inputsFrom = action.get("inputsFrom") instanceof Map ? (Map<String, Object>) action.get("inputsFrom") : null;

            if (MODEL_EVALUATION.equals(tool)) {
                // Break: save state and return control to the model
                String executionId = existingExecutionId != null ? existingExecutionId : UUID.randomUUID().toString();
                ExecutionState state = new ExecutionState(executionId, plan, initialInput, contextLocal, results, i + 1, authCode, partnerClientCode);
                executionStates.put(executionId, state);
                List<Map<String, Object>> nextSteps = new ArrayList<>();
                for (int j = i + 1; j < actions.size(); j++) {
                    nextSteps.add(actions.get(j));
                }
                Map<String, Object> stateMap = new HashMap<>();
                stateMap.put("initialInput", initialInput);
                stateMap.put("context", new HashMap<>(contextLocal));
                stateMap.put("completedActionIds", getCompletedActionIds(results));
                Map<String, Object> paused = new HashMap<>();
                paused.put("paused", true);
                paused.put("executionId", executionId);
                paused.put("state", stateMap);
                paused.put("resultsSoFar", results);
                paused.put("nextPossibleSteps", nextSteps);
                logger.info("Execution paused at model_evaluation ({}), executionId={}", actionId, executionId);
                return paused;
            }

            Set<String> allowedTools = toolRegistry.getTransactionOnlyToolNames();
            if (tool == null || !allowedTools.contains(tool)) {
                Map<String, Object> errResult = new HashMap<>();
                errResult.put("actionId", actionId != null ? actionId : "");
                errResult.put("tool", tool != null ? tool : "");
                errResult.put("status", "error");
                errResult.put("error", "Tool not allowed (transaction-only tools only).");
                results.add(errResult);
                allSuccess = false;
                continue;
            }

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

    private List<String> getCompletedActionIds(List<Map<String, Object>> results) {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> r : results) {
            Object id = r.get("actionId");
            if (id != null && !id.toString().isEmpty()) ids.add(id.toString());
        }
        return ids;
    }

    /**
     * Build a human-friendly presentation summary for an approved plan to help users understand it before execution.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPresentation(String planId, Map<String, Object> plan, List<Map<String, Object>> actions) {
        Map<String, Object> presentation = new HashMap<>();

        int totalSteps = actions != null ? actions.size() : 0;
        int readCount = 0;
        int writeCount = 0;
        int deleteCount = 0;

        List<Map<String, Object>> stepSummaries = new ArrayList<>();
        Set<String> dataAccess = new LinkedHashSet<>();

        for (Map<String, Object> action : actions) {
            if (action == null) continue;
            String tool = (String) action.get("tool");
            String id = (String) action.get("id");

            Map<String, Object> step = new HashMap<>();
            step.put("label", buildStepLabel(tool, id));
            step.put("tool", tool);

            String effect = "read";
            if (!MODEL_EVALUATION.equals(tool) && tool != null) {
                McpToolDefinition def = toolRegistry.getToolDefinition(tool);
                if (def != null) {
                    Boolean readOnly = def.getReadOnly();
                    Boolean destructive = def.getDestructive();
                    if (Boolean.TRUE.equals(destructive)) {
                        effect = "delete";
                        deleteCount++;
                    } else if (!Boolean.TRUE.equals(readOnly)) {
                        effect = "write";
                        writeCount++;
                    } else {
                        effect = "read";
                        readCount++;
                    }
                } else {
                    // Fallback: assume write for unknown tool definitions
                    effect = "write";
                    writeCount++;
                }
            }

            if (MODEL_EVALUATION.equals(tool)) {
                effect = "plan";
            }

            step.put("effect", effect);
            stepSummaries.add(step);

            // Infer high-level data access categories from tool name
            if (tool != null) {
                if (tool.startsWith("contacts_")) {
                    // Contacts tools typically expose name/phone/id
                    dataAccess.add("contacts:id");
                    dataAccess.add("contacts:name");
                    dataAccess.add("contacts:phone");
                } else if (tool.startsWith("messages_")) {
                    dataAccess.add("messages:content");
                    dataAccess.add("messages:recipients");
                } else if (tool.startsWith("tags_")) {
                    dataAccess.add("tags:id");
                    dataAccess.add("tags:name");
                } else if (tool.startsWith("appointments_")) {
                    dataAccess.add("appointments:id");
                    dataAccess.add("appointments:time");
                    dataAccess.add("appointments:contact");
                } else if (tool.startsWith("events_")) {
                    dataAccess.add("events:*");
                } else if (tool.startsWith("webhook_")) {
                    dataAccess.add("webhooks:*");
                }
            }
        }

        // Ensure readCount is accurate when definitions are missing
        if (readCount == 0 && writeCount == 0 && deleteCount == 0 && totalSteps > 0) {
            readCount = totalSteps;
        }

        boolean hasRead = readCount > 0;
        boolean hasWrite = writeCount > 0;
        boolean hasDelete = deleteCount > 0;

        // Title and body are intentionally generic but descriptive.
        String title;
        if (totalSteps == 1) {
            Map<String, Object> firstStep = stepSummaries.get(0);
            title = "Run 1-step action plan: " + firstStep.get("label");
        } else {
            title = "Run " + totalSteps + "-step action plan";
        }

        StringBuilder bodyBuilder = new StringBuilder();
        bodyBuilder.append("This plan will run ").append(totalSteps).append(" step");
        if (totalSteps != 1) {
            bodyBuilder.append("s");
        }
        bodyBuilder.append(". ");

        if (hasRead || hasWrite || hasDelete) {
            bodyBuilder.append("Summary of effects: ");
            if (hasRead) {
                bodyBuilder.append(readCount).append(" read");
            }
            if (hasWrite) {
                if (hasRead) bodyBuilder.append(", ");
                bodyBuilder.append(writeCount).append(" write");
            }
            if (hasDelete) {
                if (hasRead || hasWrite) bodyBuilder.append(", ");
                bodyBuilder.append(deleteCount).append(" delete");
            }
            bodyBuilder.append(". ");
        }

        if (!hasWrite && !hasDelete) {
            bodyBuilder.append("No data will be modified.");
        } else {
            bodyBuilder.append("Review carefully before approving, as some steps modify or delete data.");
        }

        presentation.put("title", title);
        presentation.put("body", bodyBuilder.toString());
        presentation.put("steps", stepSummaries);

        Map<String, Object> effects = new HashMap<>();
        effects.put("read", hasRead);
        effects.put("write", hasWrite);
        effects.put("delete", hasDelete);
        presentation.put("effects", effects);

        presentation.put("dataAccess", new ArrayList<>(dataAccess));

        return presentation;
    }

    /**
     * Build a simple human-readable label for a step based on its tool and id.
     */
    private String buildStepLabel(String tool, String id) {
        if (MODEL_EVALUATION.equals(tool)) {
            return "Model evaluation checkpoint";
        }
        if (tool == null || tool.trim().isEmpty()) {
            return id != null ? "Step " + id : "Unnamed step";
        }

        String name = tool.replace('_', ' ').trim();
        if (!name.isEmpty()) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

    /**
     * execute_continue: resume a paused execution (after model_evaluation). Model calls this with executionId to continue.
     */
    @SuppressWarnings("unchecked")
    public Object executeContinue(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("ActionListService.executeContinue called");

        Object execIdObj = arguments != null ? arguments.get("executionId") : null;
        if (execIdObj == null || !(execIdObj instanceof String) || ((String) execIdObj).trim().isEmpty()) {
            Map<String, Object> fail = new HashMap<>();
            fail.put("success", false);
            fail.put("error", "Missing or invalid 'executionId'. Use the executionId from the paused response.");
            return fail;
        }

        String executionId = ((String) execIdObj).trim();
        ExecutionState state = executionStates.get(executionId);
        if (state == null) {
            Map<String, Object> fail = new HashMap<>();
            fail.put("success", false);
            fail.put("error", "Execution not found or expired: " + executionId);
            return fail;
        }

        if (System.currentTimeMillis() - state.createdAt > EXECUTION_STATE_TTL_MS) {
            executionStates.remove(executionId);
            Map<String, Object> fail = new HashMap<>();
            fail.put("success", false);
            fail.put("error", "Execution expired. Start a new execution.");
            return fail;
        }

        Map<String, Object> plan = state.plan;
        Object actionsObj = plan.get("actions");
        Map<String, Object> initialInput = state.initialInput;
        if (actionsObj == null || !(actionsObj instanceof List)) {
            executionStates.remove(executionId);
            Map<String, Object> fail = new HashMap<>();
            fail.put("success", false);
            fail.put("error", "Invalid stored plan.");
            return fail;
        }

        List<Map<String, Object>> actions = (List<Map<String, Object>>) actionsObj;
        Object result = runPlanFromIndex(plan, initialInput, actions, state.nextIndex,
                state.context, state.resultsSoFar, state.authCode, state.partnerClientCode, executionId);

        if (result instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) result).get("paused"))) {
            return result;
        }
        executionStates.remove(executionId);
        return wrapLargeResultIfNeeded(result);
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

