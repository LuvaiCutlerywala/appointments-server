package com.textellent.mcp.services;

import com.textellent.mcp.services.dsl.DslPlanValidator;
import com.textellent.mcp.services.dsl.DslSimplePlanExecutor;
import com.textellent.mcp.services.dsl.DslTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Stateless, synchronous orchestration runtime for the MCP Orchestration DSL.
 * This service is the entry point for the dsl_execute_plan tool: it validates the plan structure,
 * delegates execution to {@link DslSimplePlanExecutor} or {@link DslTaskExecutor}, and returns the
 * result in the same call. No execution state is retained; each call is independent.
 *
 * Supports simplePlan (full) and task with toolCall and batchLoop steps; checkpoint semantics are still minimal.
 */
@Service
public class DslOrchestrationService {

    private static final Logger logger = LoggerFactory.getLogger(DslOrchestrationService.class);

    @Autowired
    private DslPlanValidator planValidator;
    @Autowired
    private DslSimplePlanExecutor simplePlanExecutor;
    @Autowired
    private DslTaskExecutor taskExecutor;

    /**
     * Entry point for the dsl_execute_plan tool. Execution is always synchronous and stateless.
     *
     * @param arguments         tool arguments (must contain "plan"; optional "dryRun")
     * @param authCode          auth code for primitive tools
     * @param partnerClientCode partner client code for primitive tools
     * @return result map with status, phases, summary, validationErrors, lastError
     */
    @SuppressWarnings("unchecked")
    public Object executePlan(Map<String, Object> arguments, String authCode, String partnerClientCode) {
        logger.info("executePlan: start dryRun={}", Boolean.TRUE.equals(arguments != null ? arguments.get("dryRun") : null));

        Map<String, Object> result = new LinkedHashMap<>();

        if (arguments == null || !arguments.containsKey("plan")) {
            logger.warn("executePlan: missing plan, aborting");
            result.put("status", "failed");
            result.put("validationErrors", Collections.singletonList("Missing required 'plan' object."));
            result.put("vars", Collections.emptyMap());
            return result;
        }

        Object planObj = arguments.get("plan");
        if (!(planObj instanceof Map)) {
            logger.warn("executePlan: plan is not an object");
            result.put("status", "failed");
            result.put("validationErrors", Collections.singletonList("'plan' must be an object."));
            result.put("vars", Collections.emptyMap());
            return result;
        }

        Map<String, Object> plan = (Map<String, Object>) planObj;
        Object version = plan.get("version");
        if (!(version instanceof String)) {
            logger.warn("executePlan: plan.version missing or not string");
            result.put("status", "failed");
            result.put("validationErrors", Collections.singletonList("plan.version must be a string, e.g. \"1.0\"."));
            result.put("vars", Collections.emptyMap());
            return result;
        }

        boolean hasTask = plan.containsKey("task");
        boolean hasSimplePlan = plan.containsKey("simplePlan");
        if (!hasTask && !hasSimplePlan) {
            logger.warn("executePlan: plan has neither task nor simplePlan");
            result.put("status", "failed");
            result.put("validationErrors", Collections.singletonList("plan must contain either 'task' or 'simplePlan'."));
            result.put("vars", Collections.emptyMap());
            return result;
        }

        boolean dryRun = Boolean.TRUE.equals(arguments.get("dryRun"));
        Map<String, Object> vars = new HashMap<>();
        result.put("phases", new ArrayList<Map<String, Object>>());

        try {
            if (hasSimplePlan) {
                Map<String, Object> simplePlan = (Map<String, Object>) plan.get("simplePlan");
                if (dryRun) {
                    List<String> errors = planValidator.validateSimplePlan(simplePlan);
                    if (!errors.isEmpty()) {
                        result.put("status", "failed");
                        result.put("validationErrors", errors);
                    } else {
                        result.put("status", "succeeded");
                        result.put("summary", "Plan validated successfully (dry run).");
                    }
                    result.put("vars", Collections.emptyMap());
                    return result;
                }
                simplePlanExecutor.execute(simplePlan, vars, authCode, partnerClientCode, result);
            } else {
                Map<String, Object> task = (Map<String, Object>) plan.get("task");
                if (dryRun) {
                    List<String> errors = planValidator.validateTask(task);
                    if (!errors.isEmpty()) {
                        result.put("status", "failed");
                        result.put("validationErrors", errors);
                    } else {
                        result.put("status", "succeeded");
                        result.put("summary", "Task plan validated successfully (dry run).");
                    }
                    result.put("vars", Collections.emptyMap());
                    return result;
                }
                taskExecutor.execute(task, vars, authCode, partnerClientCode, result);
            }
        } catch (Exception e) {
            logger.error("executePlan: execution failed", e);
            result.put("status", "failed");
            result.put("lastError", e.getMessage());
            if (!result.containsKey("summary")) {
                result.put("summary", "Plan execution failed with an internal error.");
            }
            if (!result.containsKey("vars")) {
                result.put("vars", Collections.emptyMap());
            }
        }

        logger.info("executePlan: finished status={}", result.get("status"));
        return result;
    }
}
