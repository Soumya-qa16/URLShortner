package com.example.orchestration.policy;

import com.example.orchestration.AutoApprovingApprovalPort;
import com.example.orchestration.OrchestrationEngine;
import com.example.orchestration.PolicyDecision;
import com.example.orchestration.PolicyGuard;
import com.example.orchestration.RetryPolicy;
import com.example.orchestration.Stage;
import com.example.orchestration.StageOutput;
import com.example.orchestration.StageStatus;
import com.example.orchestration.WorkflowContext;
import com.example.orchestration.WorkflowDefinition;
import com.example.orchestration.WorkflowResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers policy guardrails (security/compliance/change-control checks). A
 * denial is a hard stop, deliberately evaluated <em>before</em> retries and
 * fallback so governance can't be routed around by either of those paths.
 */
class OrchestrationPolicyTest {

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void policyGuardDenial_blocksStageBeforeAgentEverRuns() {
        AtomicInteger agentInvocations = new AtomicInteger(0);
        Stage a = Stage.builder("a").agent(ctx -> {
            agentInvocations.incrementAndGet();
            return StageOutput.of("should never run");
        }).build();
        WorkflowDefinition workflow = WorkflowDefinition.of(a);
        PolicyGuard denyAll = (stage, ctx) -> PolicyDecision.deny("blocked for this test");
        OrchestrationEngine engine = new OrchestrationEngine(
                workflow, new AutoApprovingApprovalPort(), denyAll, executor);

        WorkflowResult result = engine.run(new WorkflowContext());

        assertThat(result.statuses().get("a")).isEqualTo(StageStatus.FAILED);
        assertThat(agentInvocations.get()).isZero();
    }

    @Test
    void policyGuardDenial_cannotBeBypassedByRetryOrFallback() {
        AtomicInteger agentInvocations = new AtomicInteger(0);
        AtomicInteger fallbackInvocations = new AtomicInteger(0);
        Stage a = Stage.builder("a")
                .retryPolicy(RetryPolicy.of(3, java.time.Duration.ZERO))
                .agent(ctx -> {
                    agentInvocations.incrementAndGet();
                    return StageOutput.of("should never run");
                })
                .fallback(ctx -> {
                    fallbackInvocations.incrementAndGet();
                    return StageOutput.of("should never run either");
                })
                .build();
        WorkflowDefinition workflow = WorkflowDefinition.of(a);
        PolicyGuard denyAll = (stage, ctx) -> PolicyDecision.deny("hard compliance stop");
        OrchestrationEngine engine = new OrchestrationEngine(
                workflow, new AutoApprovingApprovalPort(), denyAll, executor);

        WorkflowResult result = engine.run(new WorkflowContext());

        assertThat(result.statuses().get("a")).isEqualTo(StageStatus.FAILED);
        assertThat(agentInvocations.get()).isZero();
        assertThat(fallbackInvocations.get()).isZero();
    }

    @Test
    void policyGuardAllow_letsStageProceedNormally() {
        Stage a = Stage.builder("a").agent(ctx -> StageOutput.of("ran fine")).build();
        WorkflowDefinition workflow = WorkflowDefinition.of(a);
        OrchestrationEngine engine = new OrchestrationEngine(
                workflow, new AutoApprovingApprovalPort(), PolicyGuard.allowAll(), executor);

        WorkflowResult result = engine.run(new WorkflowContext());

        assertThat(result.statuses().get("a")).isEqualTo(StageStatus.COMPLETED);
    }
}
