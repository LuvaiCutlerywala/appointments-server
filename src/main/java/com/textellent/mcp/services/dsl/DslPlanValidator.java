package com.textellent.mcp.services.dsl;

import com.textellent.mcp.registry.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Validates DSL plan structure: simplePlan (steps, tool names) and task (phases, step types, tool names).
 * Does not execute; used for dry-run and before execution.
 */
@Component
public class DslPlanValidator {

    private static final Logger logger = LoggerFactory.getLogger(DslPlanValidator.class);

    @Lazy
    private final McpToolRegistry toolRegistry;

    public DslPlanValidator(@Lazy McpToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * Validate simplePlan: steps array, unique ids, required tool, tool exists in registry.
     */
    @SuppressWarnings("unchecked")
    public List<String> validateSimplePlan(Map<String, Object> simplePlan) {
        List<String> errors = new ArrayList<>();
        if (simplePlan == null) {
            logger.warn("validateSimplePlan: simplePlan is null");
            errors.add("simplePlan must be an object.");
            return errors;
        }
        Object stepsObj = simplePlan.get("steps");
        if (!(stepsObj instanceof List)) {
            logger.warn("validateSimplePlan: steps is not an array");
            errors.add("simplePlan.steps must be an array.");
            return errors;
        }

        List<Map<String, Object>> steps = (List<Map<String, Object>>) stepsObj;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            String id = (String) step.get("id");
            String tool = (String) step.get("tool");
            if (id == null || id.trim().isEmpty()) {
                errors.add("simplePlan step at index " + i + " is missing 'id'.");
            } else if (!ids.add(id)) {
                errors.add("Duplicate simplePlan step id: " + id);
            }
            if (tool == null || tool.trim().isEmpty()) {
                errors.add("simplePlan step " + (id != null ? id : ("at index " + i)) + " is missing 'tool'.");
            } else if (!toolRegistry.hasTool(tool)) {
                errors.add("simplePlan step " + (id != null ? id : ("at index " + i)) + " references unknown tool '" + tool + "'.");
            }
        }
        if (!errors.isEmpty()) {
            logger.info("validateSimplePlan: validation failed with {} error(s)", errors.size());
        } else {
            logger.debug("validateSimplePlan: valid, {} steps", steps.size());
        }
        return errors;
    }

    /**
     * Validate task: phases array, phase ids, steps array per phase, step id/type, toolCall tool exists.
     */
    @SuppressWarnings("unchecked")
    public List<String> validateTask(Map<String, Object> task) {
        List<String> errors = new ArrayList<>();
        if (task == null) {
            logger.warn("validateTask: task is null");
            errors.add("task must be an object.");
            return errors;
        }
        Object phasesObj = task.get("phases");
        if (!(phasesObj instanceof List)) {
            logger.warn("validateTask: phases is not an array");
            errors.add("task.phases must be an array.");
            return errors;
        }
        List<Map<String, Object>> phases = (List<Map<String, Object>>) phasesObj;
        Set<String> phaseIds = new HashSet<>();
        for (int i = 0; i < phases.size(); i++) {
            Map<String, Object> phase = phases.get(i);
            String phaseId = (String) phase.get("id");
            if (phaseId == null || phaseId.trim().isEmpty()) {
                errors.add("Phase at index " + i + " is missing 'id'.");
                continue;
            } else if (!phaseIds.add(phaseId)) {
                errors.add("Duplicate phase id: " + phaseId);
            }
            Object stepsObj = phase.get("steps");
            if (!(stepsObj instanceof List)) {
                errors.add("Phase " + phaseId + " must have array 'steps'.");
                continue;
            }
            List<Map<String, Object>> steps = (List<Map<String, Object>>) stepsObj;
            Set<String> stepIds = new HashSet<>();
            for (int j = 0; j < steps.size(); j++) {
                Map<String, Object> step = steps.get(j);
                String stepId = (String) step.get("id");
                String type = (String) step.get("type");
                if (stepId == null || stepId.trim().isEmpty()) {
                    errors.add("Phase " + phaseId + " step at index " + j + " is missing 'id'.");
                } else if (!stepIds.add(stepId)) {
                    errors.add("Phase " + phaseId + " has duplicate step id: " + stepId);
                }
                if (type == null || type.trim().isEmpty()) {
                    errors.add("Phase " + phaseId + " step " + stepId + " is missing 'type'.");
                }
                if ("toolCall".equals(type)) {
                    String tool = (String) step.get("tool");
                    if (tool == null || tool.trim().isEmpty()) {
                        errors.add("Phase " + phaseId + " step " + stepId + " (toolCall) is missing 'tool'.");
                    } else if (!toolRegistry.hasTool(tool)) {
                        errors.add("Phase " + phaseId + " step " + stepId + " (toolCall) references unknown tool '" + tool + "'.");
                    }
                }
                if ("batchLoop".equals(type)) {
                    Map<String, Object> src = step.get("source") instanceof Map
                            ? (Map<String, Object>) step.get("source")
                            : null;
                    if (src == null) {
                        errors.add("Phase " + phaseId + " step " + stepId + " (batchLoop) requires 'source' object.");
                    } else {
                        String fv = (String) src.get("fromVar");
                        if (fv == null || fv.trim().isEmpty()) {
                            errors.add("Phase " + phaseId + " step " + stepId + " (batchLoop) requires source.fromVar.");
                        }
                    }
                    Map<String, Object> body = step.get("bodyStep") instanceof Map
                            ? (Map<String, Object>) step.get("bodyStep")
                            : null;
                    if (body == null) {
                        errors.add("Phase " + phaseId + " step " + stepId + " (batchLoop) requires 'bodyStep'.");
                    } else {
                        String bt = (String) body.get("tool");
                        if (bt == null || bt.trim().isEmpty()) {
                            errors.add("Phase " + phaseId + " step " + stepId + " (batchLoop) bodyStep is missing 'tool'.");
                        } else if (!toolRegistry.hasTool(bt)) {
                            errors.add("Phase " + phaseId + " step " + stepId + " (batchLoop) bodyStep references unknown tool '" + bt + "'.");
                        }
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            logger.info("validateTask: validation failed with {} error(s), phases={}", errors.size(), phases.size());
        } else {
            logger.debug("validateTask: valid, {} phases", phases.size());
        }
        return errors;
    }
}
