package com.example.orchestration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Append-only, thread-safe audit trail for one workflow run. This is what makes
 * the engine's execution "audit-grade observable" -- every gate check, retry,
 * approval, rollback, and re-plan is recorded here with a timestamp and the
 * plan revision it occurred under.
 */
public final class AuditLog {

    private final List<AuditEvent> events = Collections.synchronizedList(new ArrayList<>());
    private final String runId;

    public AuditLog(String runId) {
        this.runId = runId;
    }

    public void record(String stageId, AuditEventType type, String message, int planRevision) {
        events.add(new AuditEvent(java.time.Instant.now(), runId, stageId, type, message, planRevision));
    }

    public List<AuditEvent> events() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    /** Renders the full trail as human-readable text, in the order events occurred. */
    public String renderTrail() {
        StringBuilder sb = new StringBuilder();
        for (AuditEvent e : events()) {
            sb.append(String.format("[%s] rev=%d %-26s %-24s %s%n",
                    e.timestamp(), e.planRevision(), e.type(), e.stageId(), e.message()));
        }
        return sb.toString();
    }
}
