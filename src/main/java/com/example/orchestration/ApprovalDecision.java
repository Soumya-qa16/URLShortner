package com.example.orchestration;

/**
 * The result of a human approval checkpoint for a high-impact stage.
 */
public record ApprovalDecision(boolean approved, String approver, String comment) {
}
