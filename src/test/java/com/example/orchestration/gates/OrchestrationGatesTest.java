package com.example.orchestration.gates;

import com.example.orchestration.AutoApprovingApprovalPort;
import com.example.orchestration.GateResult;
import com.example.orchestration.OrchestrationEngine;
import com.example.orchestration.PolicyGuard;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers entry gates (preconditions checked before a stage's agent runs) and
 * exit gates (postconditions/acceptance checks on a stage's output) -- a
 * failure of either is treated identically to the agent itself failing.
 */
class OrchestrationGatesTest {

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private OrchestrationEngine engine(WorkflowDefinition workflow) {
        return new OrchestrationEngine(workflow, new AutoApprovingApprovalPort(), PolicyGuard.allowAll(), executor);
    }

    @Test
    void entryGateFailure_failsStageAndSkipsDownstream() {
        Stage a = Stage.builder("a")
                .entryGate(ctx -> GateResult.fail("precondition not met"))
                .agent(ctx -> StageOutput.of("should never run"))
                .build();
        Stage b = Stage.builder("b").dependsOn("a").agent(ctx -> StageOutput.of("should never run")).build();
        WorkflowDefinition workflow = WorkflowDefinition.of(a, b);

        WorkflowResult result = engine(workflow).run(new WorkflowContext());

        assertThat(result.statuses().get("a")).isEqualTo(StageStatus.FAILED);
        assertThat(result.statuses().get("b")).isEqualTo(StageStatus.SKIPPED_UPSTREAM_FAILURE);
    }

    @Test
    void exitGateFailure_isTreatedAsStageFailure() {
        Stage a = Stage.builder("a")
                .exitGate(ctx -> GateResult.fail("output failed acceptance check"))
                .agent(ctx -> StageOutput.of("ran, but output is unacceptable"))
                .build();
        WorkflowDefinition workflow = WorkflowDefinition.of(a);

        WorkflowResult result = engine(workflow).run(new WorkflowContext());

        assertThat(result.statuses().get("a")).isEqualTo(StageStatus.FAILED);
    }
}
