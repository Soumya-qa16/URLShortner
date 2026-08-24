package com.example.orchestration;

/**
 * Abstraction for a human-in-the-loop approval checkpoint on a high-impact
 * stage. Decoupling this from the engine means a real deployment can plug in a
 * UI/ticket-queue-backed implementation that genuinely blocks until a person
 * responds, while tests and demos use a stand-in like
 * {@link AutoApprovingApprovalPort}.
 */
@FunctionalInterface
public interface ApprovalPort {
    ApprovalDecision requestApproval(Stage stage, WorkflowContext context);
}
