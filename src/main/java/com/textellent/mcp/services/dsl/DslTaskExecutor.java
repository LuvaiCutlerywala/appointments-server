package com.textellent.mcp.services.dsl;

import com.textellent.mcp.registry.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Executes the full task form of the DSL: phases with dependsOn, steps (toolCall and placeholders for batchLoop/wait/compute/checkpoint).
 */
@Component
public class DslTaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DslTaskExecutor.class);

    @Lazy
    private final McpToolRegistry toolRegistry;
    private final DslPlanValidator validator;
    private final DslTemplateResolver templateResolver;

    public DslTaskExecutor(
            @Lazy McpToolRegistry toolRegistry,
            DslPlanValidator validator,
            DslTemplateResolver templateResolver
    ) {
        this.toolRegistry = toolRegistry;
        this.validator = validator;
        this.templateResolver = templateResolver;
    }

    /**
     * Validate and execute task; write phases, status, summary into overallResult.
     */
    @SuppressWarnings("unchecked")
    public void execute(
            Map<String, Object> task,
            Map<String, Object> vars,
            String authCode,
            String partnerClientCode,
            Map<String, Object> overallResult
    ) throws Exception {
        logger.info("DslTaskExecutor: starting task execution");
        List<String> validationErrors = validator.validateTask(task);
        if (!validationErrors.isEmpty()) {
            logger.warn("DslTaskExecutor: task validation failed, aborting execution");
            overallResult.put("status", "failed");
            overallResult.put("validationErrors", validationErrors);
            overallResult.put("summary", "Task validation failed.");
            return;
        }

        List<Map<String, Object>> phases = (List<Map<String, Object>>) task.get("phases");
        Map<String, String> phaseStatus = new LinkedHashMap<>();
        List<Map<String, Object>> phaseSummaries = new ArrayList<>();

        for (int p = 0; p < phases.size(); p++) {
            Map<String, Object> phase = phases.get(p);
            String phaseId = (String) phase.get("id");
            List<String> dependsOn = phase.get("dependsOn") instanceof List
                    ? (List<String>) phase.get("dependsOn")
                    : Collections.emptyList();

            boolean dependencyFailed = dependsOn.stream()
                    .anyMatch(dep -> !"succeeded".equals(phaseStatus.get(dep)));
            if (dependencyFailed) {
                logger.info("DslTaskExecutor: phase {} skipped (dependency failed)", phaseId);
                phaseStatus.put(phaseId, "skipped");
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("id", phaseId);
                summary.put("status", "skipped");
                summary.put("error", "Skipped due to failed dependency.");
                phaseSummaries.add(summary);
                continue;
            }

            logger.info("DslTaskExecutor: executing phase {}/{} id={}", p + 1, phases.size(), phaseId);
            Map<String, Object> phaseResult = executePhase(phase, vars, authCode, partnerClientCode);
            String status = (String) phaseResult.getOrDefault("status", "succeeded");
            phaseStatus.put(phaseId, status);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("id", phaseId);
            summary.put("status", status);
            if (phaseResult.containsKey("error")) {
                summary.put("error", phaseResult.get("error"));
            }
            phaseSummaries.add(summary);
        }

        overallResult.put("phases", phaseSummaries);
        boolean anyFailed = phaseStatus.values().stream().anyMatch(s -> "failed".equals(s));
        overallResult.put("status", anyFailed ? "failed" : "succeeded");
        String taskDescription = (String) task.getOrDefault("description", "Task");
        overallResult.put("summary", taskDescription + " completed with status: " + overallResult.get("status") + ".");
        overallResult.put("vars", new LinkedHashMap<>(vars));
        logger.info("DslTaskExecutor: task finished, status={} varsKeys={}", overallResult.get("status"), vars.keySet());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executePhase(
            Map<String, Object> phase,
            Map<String, Object> vars,
            String authCode,
            String partnerClientCode
    ) throws Exception {
        String phaseId = (String) phase.get("id");
        Map<String, Object> phaseResult = new LinkedHashMap<>();
        List<Map<String, Object>> steps = (List<Map<String, Object>>) phase.get("steps");

        for (int s = 0; s < steps.size(); s++) {
            Map<String, Object> step = steps.get(s);
            String type = (String) step.get("type");
            String stepId = (String) step.get("id");
            logger.debug("DslTaskExecutor: phase {} step {}/{} id={} type={}", phaseId, s + 1, steps.size(), stepId, type);

            try {
                switch (type != null ? type : "") {
                    case "toolCall":
                        executeToolCallStep(step, vars, authCode, partnerClientCode);
                        break;
                    case "batchLoop":
                        executeBatchLoopStep(step, vars, authCode, partnerClientCode);
                        break;
                    case "wait":
                        logger.info("DslTaskExecutor: ignoring wait step {} (MVP)", stepId);
                        break;
                    case "compute":
                        logger.info("DslTaskExecutor: ignoring compute step {} (MVP)", stepId);
                        break;
                    case "checkpoint":
                        logger.info("DslTaskExecutor: checkpoint step {} not yet active; continuing", stepId);
                        break;
                    default:
                        logger.warn("DslTaskExecutor: unknown step type '{}' for id={}, skipping", type, stepId);
                }
            } catch (Exception e) {
                logger.error("DslTaskExecutor: step id={} type={} failed: {}", stepId, type, e.getMessage(), e);
                phaseResult.put("status", "failed");
                phaseResult.put("error", "Step " + stepId + " failed: " + e.getMessage());
                return phaseResult;
            }
        }

        phaseResult.put("status", "succeeded");
        return phaseResult;
    }

    @SuppressWarnings("unchecked")
    private void executeToolCallStep(
            Map<String, Object> step,
            Map<String, Object> vars,
            String authCode,
            String partnerClientCode
    ) throws Exception {
        String tool = (String) step.get("tool");
        Map<String, Object> args = step.get("args") instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) step.get("args"))
                : new LinkedHashMap<>();
        Map<String, Object> resolvedArgs = templateResolver.resolveTemplatesInArgs(args, vars, null);
        Object raw = toolRegistry.execute(tool, resolvedArgs, authCode, partnerClientCode);
        Object parsed = templateResolver.maybeParseJson(raw);
        String capture = (String) step.get("capture");
        if (capture != null && !capture.trim().isEmpty()) {
            vars.put(capture, parsed);
            logger.debug("DslTaskExecutor: captured into vars.{}", capture);
        }
    }

    /**
     * Runs {@code bodyStep} once per batch chunk from {@code source} (a variable, optional JSON path to an array).
     * Batch context for templates: {@code batch.items} (current chunk), {@code batch.item} (set when chunk size is 1),
     * {@code batch.batchIndex}, {@code batch.itemOffset}. See {@code DslTemplateResolver} for {@code batch.items[*].id}.
     */
    @SuppressWarnings("unchecked")
    private void executeBatchLoopStep(
            Map<String, Object> step,
            Map<String, Object> vars,
            String authCode,
            String partnerClientCode
    ) throws Exception {
        Map<String, Object> source = step.get("source") instanceof Map
                ? (Map<String, Object>) step.get("source")
                : null;
        if (source == null) {
            throw new IllegalArgumentException("batchLoop step missing 'source' object.");
        }
        String fromVar = (String) source.get("fromVar");
        if (fromVar == null || fromVar.trim().isEmpty()) {
            throw new IllegalArgumentException("batchLoop source missing 'fromVar'.");
        }
        String path = source.get("path") instanceof String ? ((String) source.get("path")).trim() : "";

        Object root = vars.get(fromVar);
        if (root == null) {
            throw new IllegalArgumentException("batchLoop source variable '" + fromVar + "' is missing or null.");
        }
        Object listObj = root;
        if (!path.isEmpty()) {
            if (!(root instanceof Map)) {
                throw new IllegalArgumentException(
                        "batchLoop cannot apply path '" + path + "' because variable '" + fromVar + "' is not an object.");
            }
            listObj = templateResolver.resolvePath((Map<String, Object>) root, path);
        }
        if (!(listObj instanceof List)) {
            String kind = listObj == null ? "null" : listObj.getClass().getSimpleName();
            throw new IllegalArgumentException("batchLoop source must resolve to a JSON array; got: " + kind);
        }

        List<Object> allItems = new ArrayList<>((List<Object>) listObj);

        Map<String, Object> bodyStep = step.get("bodyStep") instanceof Map
                ? (Map<String, Object>) step.get("bodyStep")
                : null;
        if (bodyStep == null) {
            throw new IllegalArgumentException("batchLoop step missing 'bodyStep'.");
        }
        String tool = (String) bodyStep.get("tool");
        if (tool == null || tool.trim().isEmpty()) {
            throw new IllegalArgumentException("batchLoop bodyStep missing 'tool'.");
        }

        Map<String, Object> batchSpec = step.get("batch") instanceof Map
                ? (Map<String, Object>) step.get("batch")
                : Collections.emptyMap();
        int chunkSize = 1;
        if (batchSpec.get("size") instanceof Number) {
            chunkSize = Math.max(1, ((Number) batchSpec.get("size")).intValue());
        }
        int maxBatches = Integer.MAX_VALUE;
        if (batchSpec.get("maxBatches") instanceof Number) {
            maxBatches = Math.max(1, ((Number) batchSpec.get("maxBatches")).intValue());
        }
        long throttleMs = 0;
        if (batchSpec.get("throttleMsBetweenBatches") instanceof Number) {
            throttleMs = Math.max(0, ((Number) batchSpec.get("throttleMsBetweenBatches")).longValue());
        }

        Map<String, Object> argsTemplate = bodyStep.get("argsTemplate") instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) bodyStep.get("argsTemplate"))
                : new LinkedHashMap<>();

        int i = 0;
        int batchIndex = 0;
        while (i < allItems.size() && batchIndex < maxBatches) {
            int end = Math.min(i + chunkSize, allItems.size());
            List<Object> chunk = new ArrayList<>(allItems.subList(i, end));

            Map<String, Object> batchContext = new LinkedHashMap<>();
            batchContext.put("items", chunk);
            batchContext.put("batchIndex", batchIndex);
            batchContext.put("itemOffset", i);
            if (chunk.size() == 1) {
                batchContext.put("item", chunk.get(0));
            }

            Map<String, Object> resolvedArgs = templateResolver.resolveTemplatesInArgs(argsTemplate, vars, batchContext);
            Object raw = toolRegistry.execute(tool, resolvedArgs, authCode, partnerClientCode);
            Object parsed = templateResolver.maybeParseJson(raw);
            String capture = (String) bodyStep.get("capture");
            if (capture != null && !capture.trim().isEmpty()) {
                vars.put(capture, parsed);
                logger.debug("DslTaskExecutor: batchLoop captured into vars.{}", capture);
            }

            batchIndex++;
            i = end;
            if (throttleMs > 0 && i < allItems.size() && batchIndex < maxBatches) {
                try {
                    Thread.sleep(throttleMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("batchLoop interrupted during throttle", ie);
                }
            }
        }
        logger.info("DslTaskExecutor: batchLoop finished, {} batch(es), {} item(s) total", batchIndex, allItems.size());
    }
}
