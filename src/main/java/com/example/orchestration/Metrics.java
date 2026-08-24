package com.example.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reliability metrics for one workflow run: success rate, retry/rollback
 * frequency, mean time to recover (MTTR), and end-to-end latency, plus
 * per-stage attempt counts and latencies for finer-grained observability.
 */
public final class Metrics {

    private final Instant startedAt = Instant.now();
    private volatile Instant finishedAt;

    private final Map<String, Integer> attemptsByStage = new ConcurrentHashMap<>();
    private final Map<String, Duration> latencyByStage = new ConcurrentHashMap<>();
    private final Map<String, Instant> firstFailureAt = new ConcurrentHashMap<>();
    private final Map<String, Duration> recoveryTimeByStage = new ConcurrentHashMap<>();

    private final AtomicInteger retryCount = new AtomicInteger();
    private final AtomicInteger rollbackCount = new AtomicInteger();
    private final AtomicInteger fallbackCount = new AtomicInteger();
    private final AtomicInteger approvalCount = new AtomicInteger();
    private final AtomicInteger replanCount = new AtomicInteger();
    private final AtomicInteger succeededStages = new AtomicInteger();
    private final AtomicInteger failedStages = new AtomicInteger();

    private volatile int totalStages;

    public void setTotalStages(int n) {
        this.totalStages = n;
    }

    public void recordAttempt(String stageId) {
        attemptsByStage.merge(stageId, 1, Integer::sum);
    }

    public void recordRetry() {
        retryCount.incrementAndGet();
    }

    public void recordRollback() {
        rollbackCount.incrementAndGet();
    }

    public void recordFallback() {
        fallbackCount.incrementAndGet();
    }

    public void recordApproval() {
        approvalCount.incrementAndGet();
    }

    public void recordReplan() {
        replanCount.incrementAndGet();
    }

    public void recordStageLatency(String stageId, Duration d) {
        latencyByStage.put(stageId, d);
    }

    public void recordFirstFailure(String stageId) {
        firstFailureAt.putIfAbsent(stageId, Instant.now());
    }

    /** Call when a stage that previously failed at least once finally succeeds. */
    public void recordRecovery(String stageId) {
        Instant firstFailure = firstFailureAt.get(stageId);
        if (firstFailure != null) {
            recoveryTimeByStage.put(stageId, Duration.between(firstFailure, Instant.now()));
        }
    }

    public void recordSucceeded() {
        succeededStages.incrementAndGet();
    }

    public void recordFailed() {
        failedStages.incrementAndGet();
    }

    public void finish() {
        this.finishedAt = Instant.now();
    }

    public double successRate() {
        return totalStages == 0 ? 0.0 : (double) succeededStages.get() / totalStages;
    }

    public double retryFrequency() {
        return totalStages == 0 ? 0.0 : (double) retryCount.get() / totalStages;
    }

    public double rollbackFrequency() {
        return totalStages == 0 ? 0.0 : (double) rollbackCount.get() / totalStages;
    }

    public Duration meanTimeToRecover() {
        if (recoveryTimeByStage.isEmpty()) {
            return Duration.ZERO;
        }
        long avgMillis = (long) recoveryTimeByStage.values().stream()
                .mapToLong(Duration::toMillis)
                .average()
                .orElse(0);
        return Duration.ofMillis(avgMillis);
    }

    public Duration endToEndLatency() {
        Instant end = finishedAt != null ? finishedAt : Instant.now();
        return Duration.between(startedAt, end);
    }

    public Map<String, Integer> getAttemptsByStage() {
        return Map.copyOf(attemptsByStage);
    }

    public Map<String, Duration> getLatencyByStage() {
        return Map.copyOf(latencyByStage);
    }

    public int getRetryCount() {
        return retryCount.get();
    }

    public int getRollbackCount() {
        return rollbackCount.get();
    }

    public int getFallbackCount() {
        return fallbackCount.get();
    }

    public int getApprovalCount() {
        return approvalCount.get();
    }

    public int getReplanCount() {
        return replanCount.get();
    }

    public int getSucceededStages() {
        return succeededStages.get();
    }

    public int getFailedStages() {
        return failedStages.get();
    }

    public String renderSummary() {
        return String.format(
                "Stages: %d | Succeeded: %d | Failed: %d | Success rate: %.1f%%%n"
                        + "Retries: %d (%.2f/stage) | Rollbacks: %d (%.2f/stage) | Fallbacks used: %d%n"
                        + "Approvals requested: %d | Re-plans triggered: %d%n"
                        + "MTTR: %s | End-to-end latency: %s",
                totalStages, succeededStages.get(), failedStages.get(), successRate() * 100,
                retryCount.get(), retryFrequency(), rollbackCount.get(), rollbackFrequency(), fallbackCount.get(),
                approvalCount.get(), replanCount.get(), meanTimeToRecover(), endToEndLatency());
    }
}
