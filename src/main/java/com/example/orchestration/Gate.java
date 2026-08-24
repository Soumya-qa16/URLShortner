package com.example.orchestration;

/**
 * An entry or exit precondition/postcondition check on a stage. Entry gates run
 * before the stage's agent executes; exit gates run after it succeeds, acting as
 * an acceptance test on the stage's output (a failed exit gate is treated the
 * same as a failed execution -- it triggers rollback/safe-stop).
 */
@FunctionalInterface
public interface Gate {
    GateResult check(WorkflowContext context);

    static Gate alwaysPass() {
        return ctx -> GateResult.pass("no gate condition defined");
    }
}
