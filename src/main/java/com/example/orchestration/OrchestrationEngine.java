package com.example.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Executes a {@link WorkflowDefinition} against a {@link WorkflowContext}.
 *
 * <p>This is the orchestration "critical differentiator": it drives non-linear,
 * stateful execution over an explicit dependency graph rather than a simple
 * linear chain --
 * <ul>
 *   <li>independent stages run in parallel; a stage with multiple dependencies
 *       acts as a synchronization join and only starts once all of them have
 *       completed;</li>
 *   <li>every stage passes through a policy guardrail, an entry gate, an
 *       optional human-approval checkpoint, bounded retries, an optional
 *       fallback agent, and an exit gate, in that order;</li>
 *   <li>a stage that fails unrecoverably triggers rollback of its rollback
 *       group and a "safe-stop" that skips (rather than crashes) everything
 *       downstream of it, while unrelated branches keep running;</li>
 *   <li>an agent can call {@link WorkflowContext#markStale} on an
 *       already-completed stage to trigger dynamic re-planning: that stage and
 *       everything transitively downstream of it is reset and re-executed
 *       under a new plan revision, while unrelated context is preserved.</li>
 * </ul>
 *
 * <p>Every one of these transitions is written to the {@link AuditLog}, and
 * every attempt/retry/rollback/approval is counted in {@link Metrics}.
 */
public final class OrchestrationEngine {

    private final WorkflowDefinition workflow;
    private final ApprovalPort approvalPort;
    private final PolicyGuard policyGuard;
    private final ExecutorService executor;

    public OrchestrationEngine(WorkflowDefinition workflow, ApprovalPort approvalPort,
                                PolicyGuard policyGuard, ExecutorService executor) {
        this.workflow = Objects.requireNonNull(workflow);
        this.approvalPort = Objects.requireNonNull(approvalPort);
        this.policyGuard = Objects.requireNonNull(policyGuard);
        this.executor = Objects.requireNonNull(executor);
    }

    public WorkflowResult run(WorkflowContext context) {
        Metrics metrics = new Metrics();
        metrics.setTotalStages(workflow.getStages().size());

        Map<String, StageStatus> statuses = new ConcurrentHashMap<>();
        for (Stage s : workflow.getStages()) {
            statuses.put(s.getId(), StageStatus.PENDING);
        }
        Map<String, CompletableFuture<Void>> futures = new ConcurrentHashMap<>();

        while (true) {
            boolean replanned = handleReplanning(context, statuses, metrics);
            boolean startedAny = false;

            for (Stage stage : workflow.getStages()) {
                if (statuses.get(stage.getId()) != StageStatus.PENDING) {
                    continue;
                }
                boolean anyDepFailed = stage.getDependsOn().stream().anyMatch(dep ->
                        statuses.get(dep) == StageStatus.FAILED
                                || statuses.get(dep) == StageStatus.SKIPPED_UPSTREAM_FAILURE);
                if (anyDepFailed) {
                    statuses.put(stage.getId(), StageStatus.SKIPPED_UPSTREAM_FAILURE);
                    metrics.recordFailed();
                    context.getAuditLog().record(stage.getId(), AuditEventType.SKIPPED_UPSTREAM_FAILURE,
                            "Skipped: an upstream dependency failed or was rolled back.", context.getPlanRevision());
                    startedAny = true;
                    continue;
                }
                boolean depsSatisfied = stage.getDependsOn().stream()
                        .allMatch(dep -> statuses.get(dep) == StageStatus.COMPLETED);
                if (!depsSatisfied) {
                    continue; // not ready yet
                }
                statuses.put(stage.getId(), StageStatus.RUNNING);
                startedAny = true;
                CompletableFuture<Void> future = CompletableFuture.runAsync(
                        () -> executeStage(stage, context, statuses, metrics), executor);
                futures.put(stage.getId(), future);
            }

            if (!replanned && !startedAny) {
                List<CompletableFuture<Void>> running = statuses.entrySet().stream()
                        .filter(e -> isInFlight(e.getValue()))
                        .map(e -> futures.get(e.getKey()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                if (running.isEmpty()) {
                    break; // nothing pending and nothing in flight -> the run is over
                }
                CompletableFuture.anyOf(running.toArray(new CompletableFuture[0])).join();
            }
        }

        metrics.finish();
        return new WorkflowResult(Map.copyOf(statuses), context, metrics);
    }

    private static boolean isInFlight(StageStatus status) {
        return status == StageStatus.RUNNING
                || status == StageStatus.AWAITING_APPROVAL
                || status == StageStatus.RETRYING
                || status == StageStatus.FALLBACK;
    }

    /**
     * Checks for stages marked stale (via {@link WorkflowContext#markStale}) since
     * the last pass. For each one found (and still COMPLETED, i.e. not currently
     * mid-execution), resets it and everything transitively downstream of it back
     * to PENDING under a new plan revision, preserving everything else.
     */
    private boolean handleReplanning(WorkflowContext context, Map<String, StageStatus> statuses, Metrics metrics) {
        boolean any = false;
        for (Stage stage : workflow.getStages()) {
            if (statuses.get(stage.getId()) == StageStatus.COMPLETED && context.consumeStale(stage.getId())) {
                int revision = context.bumpPlanRevision();
                metrics.recordReplan();

                Set<String> toReset = new LinkedHashSet<>();
                toReset.add(stage.getId());
                toReset.addAll(workflow.getTransitiveDependents(stage.getId()));

                for (String id : toReset) {
                    StageStatus current = statuses.get(id);
                    if (isInFlight(current)) {
                        // Can't safely rewind a stage that's actively executing in this
                        // engine; it'll be caught on a later replan pass once it settles.
                        continue;
                    }
                    statuses.put(id, StageStatus.PENDING);
                    context.getAuditLog().record(id, AuditEventType.STAGE_MARKED_STALE,
                            "Invalidated by upstream re-plan of '" + stage.getId() + "'.", revision);
                }
                context.getAuditLog().record(stage.getId(), AuditEventType.REPLAN_TRIGGERED,
                        "Upstream output changed; re-executing this stage and its dependents under plan revision "
                                + revision + ".", revision);
                any = true;
            }
        }
        return any;
    }

    private void executeStage(Stage stage, WorkflowContext context, Map<String, StageStatus> statuses,
                               Metrics metrics) {
        Instant stageStart = Instant.now();
        AuditLog log = context.getAuditLog();
        int revision = context.getPlanRevision();
        log.record(stage.getId(), AuditEventType.STARTED, "Stage became ready and started executing.", revision);

        PolicyDecision policy = policyGuard.evaluate(stage, context);
        log.record(stage.getId(), AuditEventType.POLICY_CHECKED, policy.reason(), revision);
        if (!policy.allowed()) {
            log.record(stage.getId(), AuditEventType.POLICY_DENIED, policy.reason(), revision);
            failStage(stage, context, statuses, metrics, stageStart,
                    "Blocked by policy guardrail: " + policy.reason());
            return;
        }

        GateResult entry = stage.getEntryGate().check(context);
        log.record(stage.getId(), AuditEventType.GATE_CHECKED, "entry: " + entry.reason(), revision);
        if (!entry.passed()) {
            log.record(stage.getId(), AuditEventType.GATE_FAILED, "Entry gate failed: " + entry.reason(), revision);
            failStage(stage, context, statuses, metrics, stageStart, "Entry gate failed: " + entry.reason());
            return;
        }

        if (stage.isRequiresApproval()) {
            statuses.put(stage.getId(), StageStatus.AWAITING_APPROVAL);
            metrics.recordApproval();
            log.record(stage.getId(), AuditEventType.APPROVAL_REQUESTED,
                    "High-impact stage requires human approval before proceeding.", revision);
            ApprovalDecision decision = approvalPort.requestApproval(stage, context);
            if (!decision.approved()) {
                log.record(stage.getId(), AuditEventType.REJECTED,
                        "Rejected by " + decision.approver() + ": " + decision.comment(), revision);
                failStage(stage, context, statuses, metrics, stageStart,
                        "Rejected during human approval checkpoint.");
                return;
            }
            log.record(stage.getId(), AuditEventType.APPROVED,
                    "Approved by " + decision.approver() + ": " + decision.comment(), revision);
            statuses.put(stage.getId(), StageStatus.RUNNING);
        }

        StageOutput output = null;
        boolean usedFallback = false;
        Exception lastError = null;
        int maxAttempts = Math.max(1, stage.getRetryPolicy().maxAttempts());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            metrics.recordAttempt(stage.getId());
            try {
                output = stage.getAgent().execute(context);
                lastError = null;
                break;
            } catch (Exception ex) {
                lastError = ex;
                metrics.recordFirstFailure(stage.getId());
                log.record(stage.getId(), AuditEventType.ATTEMPT_FAILED,
                        "Attempt " + attempt + "/" + maxAttempts + " failed: " + ex.getMessage(), revision);
                boolean retryable = !(ex instanceof StageExecutionException stageEx) || stageEx.isRetryable();
                if (attempt < maxAttempts && retryable) {
                    statuses.put(stage.getId(), StageStatus.RETRYING);
                    metrics.recordRetry();
                    log.record(stage.getId(), AuditEventType.RETRYING,
                            "Retrying after backoff of " + stage.getRetryPolicy().backoff(), revision);
                    sleep(stage.getRetryPolicy().backoff());
                } else {
                    break;
                }
            }
        }

        if (output == null && stage.getFallback() != null) {
            statuses.put(stage.getId(), StageStatus.FALLBACK);
            metrics.recordFallback();
            log.record(stage.getId(), AuditEventType.FALLBACK_INVOKED,
                    "Primary agent exhausted retries; invoking fallback.", revision);
            try {
                output = stage.getFallback().execute(context);
                usedFallback = true;
                lastError = null;
            } catch (Exception ex) {
                lastError = ex;
                log.record(stage.getId(), AuditEventType.FALLBACK_FAILED,
                        "Fallback also failed: " + ex.getMessage(), revision);
            }
        }

        if (output == null) {
            failStage(stage, context, statuses, metrics, stageStart,
                    "Exhausted retries" + (stage.getFallback() != null ? " and fallback" : "") + ": "
                            + (lastError != null ? lastError.getMessage() : "unknown error"));
            return;
        }

        GateResult exit = stage.getExitGate().check(context);
        log.record(stage.getId(), AuditEventType.GATE_CHECKED, "exit: " + exit.reason(), revision);
        if (!exit.passed()) {
            log.record(stage.getId(), AuditEventType.GATE_FAILED, "Exit gate failed: " + exit.reason(), revision);
            failStage(stage, context, statuses, metrics, stageStart, "Exit gate failed: " + exit.reason());
            return;
        }

        context.recordStageOutput(stage.getId(), output);
        statuses.put(stage.getId(), StageStatus.COMPLETED);
        metrics.recordSucceeded();
        metrics.recordRecovery(stage.getId());
        metrics.recordStageLatency(stage.getId(), Duration.between(stageStart, Instant.now()));
        log.record(stage.getId(), AuditEventType.COMPLETED,
                (usedFallback ? "[via fallback] " : "") + output.getSummary(), revision);
    }

    private void failStage(Stage stage, WorkflowContext context, Map<String, StageStatus> statuses,
                            Metrics metrics, Instant stageStart, String reason) {
        AuditLog log = context.getAuditLog();
        int revision = context.getPlanRevision();
        metrics.recordStageLatency(stage.getId(), Duration.between(stageStart, Instant.now()));
        rollback(stage, context, statuses, metrics);
        statuses.put(stage.getId(), StageStatus.FAILED);
        metrics.recordFailed();
        log.record(stage.getId(), AuditEventType.FAILED, reason, revision);
        log.record(stage.getId(), AuditEventType.SAFE_STOP,
                "Downstream stages depending on '" + stage.getId() + "' will be safely skipped.", revision);
    }

    /**
     * Rolls back this stage's own side effects plus any already-completed
     * sibling stage sharing its rollback group -- stages that must be undone
     * together as one logical unit when any member of the group fails.
     */
    private void rollback(Stage failedStage, WorkflowContext context, Map<String, StageStatus> statuses,
                           Metrics metrics) {
        AuditLog log = context.getAuditLog();
        int revision = context.getPlanRevision();
        for (Stage s : workflow.getStages()) {
            boolean isSelf = s.getId().equals(failedStage.getId());
            boolean sameGroupAndCompleted = !isSelf
                    && s.getRollbackGroup().equals(failedStage.getRollbackGroup())
                    && statuses.get(s.getId()) == StageStatus.COMPLETED;
            if (isSelf || sameGroupAndCompleted) {
                if (s.getRollbackAction() != null) {
                    s.getRollbackAction().accept(context);
                }
                if (!isSelf) {
                    statuses.put(s.getId(), StageStatus.ROLLED_BACK);
                }
                metrics.recordRollback();
                log.record(s.getId(), AuditEventType.ROLLED_BACK,
                        "Rolled back because '" + failedStage.getId() + "' failed (shared rollback group '"
                                + failedStage.getRollbackGroup() + "').", revision);
            }
        }
    }

    private static void sleep(Duration d) {
        if (d == null || d.isZero() || d.isNegative()) {
            return;
        }
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
