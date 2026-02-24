package com.textellent.mcp.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * State for a paused execution (at a model_evaluation step).
 * Used by ActionListService for model-in-the-loop execution.
 */
final class ExecutionState {

    final String executionId;
    final Map<String, Object> plan;
    final Map<String, Object> initialInput;
    final Map<String, Object> context;
    final List<Map<String, Object>> resultsSoFar;
    final int nextIndex;
    final String authCode;
    final String partnerClientCode;
    final long createdAt;

    ExecutionState(String executionId, Map<String, Object> plan, Map<String, Object> initialInput,
                   Map<String, Object> context, List<Map<String, Object>> resultsSoFar, int nextIndex,
                   String authCode, String partnerClientCode) {
        this.executionId = executionId;
        this.plan = plan;
        this.initialInput = initialInput;
        this.context = new HashMap<>(context);
        this.resultsSoFar = new ArrayList<>(resultsSoFar);
        this.nextIndex = nextIndex;
        this.authCode = authCode;
        this.partnerClientCode = partnerClientCode;
        this.createdAt = System.currentTimeMillis();
    }
}
