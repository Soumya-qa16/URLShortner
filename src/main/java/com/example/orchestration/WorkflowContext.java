package com.example.orchestration;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared, thread-safe state for one workflow run: cross-stage artifacts
 * (decision lineage -- what each stage produced and what later stages read),
 * the audit log, and the current plan revision counter used for dynamic
 * re-planning.
 *
 * <p>One instance is created per run and passed to every stage's agent, gate,
 * approval port, and policy guard, so context accumulates as the workflow
 * progresses and survives across a re-plan (only the stages actually
 * invalidated are re-executed; everything else in the context is preserved).
 */
public final class WorkflowContext {

    private final String runId = UUID.randomUUID().toString().substring(0, 8);
    private final ConcurrentHashMap<String, Object> artifacts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StageOutput> stageOutputs = new ConcurrentHashMap<>();
    private final AuditLog auditLog = new AuditLog(runId);
    private final AtomicInteger planRevision = new AtomicInteger(0);
    private final Set<String> staleStageIds = ConcurrentHashMap.newKeySet();

    public String getRunId() {
        return runId;
    }

    public void putArtifact(String key, Object value) {
        if (value == null) {
            artifacts.remove(key);
        } else {
            artifacts.put(key, value);
        }
    }

    public Object getArtifact(String key) {
        return artifacts.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getArtifact(String key, Class<T> type) {
        return (T) artifacts.get(key);
    }

    public void recordStageOutput(String stageId, StageOutput output) {
        stageOutputs.put(stageId, output);
    }

    public StageOutput getStageOutput(String stageId) {
        return stageOutputs.get(stageId);
    }

    public AuditLog getAuditLog() {
        return auditLog;
    }

    public int getPlanRevision() {
        return planRevision.get();
    }

    public int bumpPlanRevision() {
        return planRevision.incrementAndGet();
    }

    /** Marks a (presumably already-completed) stage as needing re-execution. */
    public void markStale(String stageId) {
        staleStageIds.add(stageId);
    }

    /** Atomically checks and clears the stale flag for a stage. */
    public boolean consumeStale(String stageId) {
        return staleStageIds.remove(stageId);
    }
}
