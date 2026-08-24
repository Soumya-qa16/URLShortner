package com.example.orchestration;

/**
 * Lifecycle states for a single stage within one workflow run.
 */
public enum StageStatus {
    PENDING,
    RUNNING,
    AWAITING_APPROVAL,
    RETRYING,
    FALLBACK,
    COMPLETED,
    FAILED,
    ROLLED_BACK,
    SKIPPED_UPSTREAM_FAILURE
}
