package com.example.orchestration;

/**
 * Categorizes each entry written to the {@link AuditLog}, giving the audit
 * trail structure that can be filtered/queried rather than being free text.
 */
public enum AuditEventType {
    STARTED,
    POLICY_CHECKED,
    POLICY_DENIED,
    GATE_CHECKED,
    GATE_FAILED,
    ATTEMPT_FAILED,
    RETRYING,
    FALLBACK_INVOKED,
    FALLBACK_FAILED,
    APPROVAL_REQUESTED,
    APPROVED,
    REJECTED,
    COMPLETED,
    FAILED,
    ROLLED_BACK,
    SKIPPED_UPSTREAM_FAILURE,
    SAFE_STOP,
    STAGE_MARKED_STALE,
    REPLAN_TRIGGERED
}
