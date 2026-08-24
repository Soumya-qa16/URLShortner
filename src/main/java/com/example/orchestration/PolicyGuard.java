package com.example.orchestration;

/**
 * A pluggable policy/compliance/change-control check, evaluated for every stage
 * before it runs. Deliberately checked <em>before</em> the retry/fallback path
 * (see {@link oldOrchestrationEngine}) so that a policy denial is a hard stop --
 * retries and fallback agents can't be used to route around governance.
 */
@FunctionalInterface
public interface PolicyGuard {
    PolicyDecision evaluate(Stage stage, WorkflowContext context);

    static PolicyGuard allowAll() {
        return (stage, ctx) -> PolicyDecision.allow();
    }
}
