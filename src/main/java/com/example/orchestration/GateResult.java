package com.example.orchestration;

/**
 * The outcome of an entry or exit gate check on a stage.
 */
public record GateResult(boolean passed, String reason) {

    public static GateResult pass(String reason) {
        return new GateResult(true, reason);
    }

    public static GateResult fail(String reason) {
        return new GateResult(false, reason);
    }
}
