package com.example.orchestration.demo;

import com.example.orchestration.PolicyDecision;
import com.example.orchestration.PolicyGuard;
import com.example.orchestration.Stage;
import com.example.orchestration.WorkflowContext;

/**
 * A concrete change-control policy for this demo workflow: release-readiness
 * requires documentation to have actually completed, and (for the greenfield
 * bulk-endpoint scenario) requires load-test evidence that was never produced.
 * Everything else is allowed unconditionally.
 *
 * <p>Deliberately evaluated by the engine <em>before</em> the retry/fallback
 * path for every stage, so a policy denial is a hard stop -- it can't be routed
 * around by retrying or falling back.
 */
public final class ReleaseReadinessPolicyGuard implements PolicyGuard {

    private final boolean denyForMissingLoadTestEvidence;

    public ReleaseReadinessPolicyGuard(boolean denyForMissingLoadTestEvidence) {
        this.denyForMissingLoadTestEvidence = denyForMissingLoadTestEvidence;
    }

    @Override
    public PolicyDecision evaluate(Stage stage, WorkflowContext context) {
        if (!"release-readiness".equals(stage.getId())) {
            return PolicyDecision.allow();
        }
        if (context.getArtifact("documentationSummary") == null) {
            return PolicyDecision.deny("Change-control violation: documentation stage has not completed.");
        }
        if (denyForMissingLoadTestEvidence) {
            return PolicyDecision.deny(
                    "Compliance violation: a new bulk-processing endpoint requires load-test evidence "
                            + "in the artifact set before release, and none was supplied.");
        }
        return PolicyDecision.allow();
    }
}
