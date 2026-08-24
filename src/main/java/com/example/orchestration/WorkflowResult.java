package com.example.orchestration;

import java.util.Map;

/**
 * The final outcome of a workflow run: every stage's terminal status, the
 * shared context (artifacts + audit log), and the reliability metrics
 * collected along the way.
 */
public record WorkflowResult(Map<String, StageStatus> statuses, WorkflowContext context, Metrics metrics) {

    public boolean isFullSuccess() {
        return statuses.values().stream().allMatch(s -> s == StageStatus.COMPLETED);
    }
}
