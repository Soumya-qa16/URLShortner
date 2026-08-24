package com.example.orchestration;

/**
 * A stand-in {@link ApprovalPort} that approves everything immediately.
 *
 * <p><b>This is for demos and tests only.</b> Auto-approving high-impact stages
 * defeats the entire point of a human approval checkpoint. A real deployment
 * must replace this with an implementation that genuinely blocks on a person's
 * decision (a UI, a ticket queue, a Slack approval bot, etc.) rather than
 * short-circuiting it in code.
 */
public final class AutoApprovingApprovalPort implements ApprovalPort {

    @Override
    public ApprovalDecision requestApproval(Stage stage, WorkflowContext context) {
        return new ApprovalDecision(
                true,
                "auto-approver (DEMO/TEST ONLY -- not a real governance control)",
                "Automatically approved for demonstration purposes.");
    }
}
