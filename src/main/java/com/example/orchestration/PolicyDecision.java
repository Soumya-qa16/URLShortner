package com.example.orchestration;

/**
 * The result of evaluating policy guardrails (security, compliance, change
 * control) against a stage before it's allowed to run.
 */
public record PolicyDecision(boolean allowed, String reason) {

    public static PolicyDecision allow() {
        return new PolicyDecision(true, "no policy violations");
    }

    public static PolicyDecision deny(String reason) {
        return new PolicyDecision(false, reason);
    }
}
