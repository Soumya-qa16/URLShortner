package com.example.orchestration;

import java.util.Map;

/**
 * What a {@link StageAgent} returns on success: a human-readable summary (shown
 * in the audit trail) plus any structured artifacts worth recording alongside
 * the ones the agent may have written directly into the {@link WorkflowContext}.
 */
public final class StageOutput {

    private final String summary;
    private final Map<String, Object> artifacts;

    public StageOutput(String summary, Map<String, Object> artifacts) {
        this.summary = summary;
        this.artifacts = artifacts == null ? Map.of() : Map.copyOf(artifacts);
    }

    public static StageOutput of(String summary) {
        return new StageOutput(summary, Map.of());
    }

    public String getSummary() {
        return summary;
    }

    public Map<String, Object> getArtifacts() {
        return artifacts;
    }
}
