package com.example.orchestration;

import java.time.Instant;

/**
 * A single, immutable audit-trail entry. {@code planRevision} ties the event to
 * the plan revision in effect when it was recorded, so a re-plan's before/after
 * can be reconstructed from the trail alone (decision lineage).
 */
public record AuditEvent(
        Instant timestamp,
        String runId,
        String stageId,
        AuditEventType type,
        String message,
        int planRevision
) {
}
