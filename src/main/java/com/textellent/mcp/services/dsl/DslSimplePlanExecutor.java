package com.textellent.mcp.services.dsl;

import com.textellent.mcp.registry.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Executes the simplePlan form of the DSL: a linear sequence of tool calls with optional capture and templates.
 */
@Component
public class DslSimplePlanExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DslSimplePlanExecutor.class);

    @Lazy
    private final McpToolRegistry toolRegistry;
    private final DslPlanValidator validator;
    private final DslTemplateResolver templateResolver;

    public DslSimplePlanExecutor(
            @Lazy McpToolRegistry toolRegistry,
            DslPlanValidator validator,
            DslTemplateResolver templateResolver
    ) {
        this.toolRegistry = toolRegistry;
        this.validator = validator;
        this.templateResolver = templateResolver;
    }

    /**
     * Validate and execute simplePlan; write status/summary into overallResult. May set status to failed and lastError.
     */
    @SuppressWarnings("unchecked")
    public void execute(
            Map<String, Object> simplePlan,
            Map<String, Object> vars,
            String authCode,
            String partnerClientCode,
            Map<String, Object> overallResult
    ) {
        logger.info("DslSimplePlanExecutor: starting simplePlan execution");
        List<String> validationErrors = validator.validateSimplePlan(simplePlan);
        if (!validationErrors.isEmpty()) {
            logger.warn("DslSimplePlanExecutor: validation failed, aborting execution");
            overallResult.put("status", "failed");
            overallResult.put("validationErrors", validationErrors);
            overallResult.put("summary", "simplePlan validation failed.");
            return;
        }

        List<Map<String, Object>> steps = (List<Map<String, Object>>) simplePlan.get("steps");
        logger.info("DslSimplePlanExecutor: executing {} steps sequentially", steps.size());

        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            String id = (String) step.get("id");
            String tool = (String) step.get("tool");
            logger.info("DslSimplePlanExecutor: step {}/{} id={} tool={}", i + 1, steps.size(), id, tool);

            Map<String, Object> args = step.get("args") instanceof Map
                    ? new LinkedHashMap<>((Map<String, Object>) step.get("args"))
                    : new LinkedHashMap<>();
            Map<String, Object> resolvedArgs = templateResolver.resolveTemplatesInArgs(args, vars, null);

            try {
                Object raw = toolRegistry.execute(tool, resolvedArgs, authCode, partnerClientCode);
                Object parsed = templateResolver.maybeParseJson(raw);
                String capture = (String) step.get("capture");
                if (capture != null && !capture.trim().isEmpty()) {
                    vars.put(capture, parsed);
                    logger.debug("DslSimplePlanExecutor: captured step output into vars.{}", capture);
                }
            } catch (Exception e) {
                logger.error("DslSimplePlanExecutor: step id={} tool={} failed: {}", id, tool, e.getMessage(), e);
                overallResult.put("status", "failed");
                overallResult.put("lastError", e.getMessage());
                // Continue through remaining steps; policy can be tightened later.
            }
        }

        if (!overallResult.containsKey("status")) {
            overallResult.put("status", "succeeded");
        }
        overallResult.put("summary",
                "Executed simplePlan with " + steps.size() + " steps. Final status: " + overallResult.get("status") + ".");
        overallResult.put("vars", new LinkedHashMap<>(vars));
        logger.info("DslSimplePlanExecutor: finished, status={} varsKeys={}", overallResult.get("status"), vars.keySet());
    }
}
