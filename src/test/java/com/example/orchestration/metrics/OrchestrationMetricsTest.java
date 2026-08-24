package com.example.orchestration.metrics;

import com.example.orchestration.AutoApprovingApprovalPort;
import com.example.orchestration.OrchestrationEngine;
import com.example.orchestration.PolicyGuard;
import com.example.orchestration.Stage;
import com.example.orchestration.StageExecutionException;
import com.example.orchestration.StageOutput;
import com.example.orchestration.WorkflowContext;
import com.example.orchestration.WorkflowDefinition;
import com.example.orchestration.WorkflowResult;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the reliability metrics collected during a run: success rate,
 * per-stage attempt counts, and that succeeded/failed counts add up correctly
 * for a mixed outcome.
 */
class OrchestrationMetricsTest {

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
    void metrics_reflectMixedSuccessAndFailureOutcome() {
        Stage ok1 = Stage.builder("ok1").agent(ctx -> StageOutput.of("fine")).build();
        Stage ok2 = Stage.builder("ok2").agent(ctx -> StageOutput.of("fine")).build();
        Stage broken = Stage.builder("broken").agent(ctx -> {
            throw new StageExecutionException("nope", false);
        }).build();
        WorkflowDefinition workflow = WorkflowDefinition.of(ok1, ok2, broken);

        WorkflowResult result = engine(workflow).run(new WorkflowContext());

        assertThat(result.metrics().getSucceededStages()).isEqualTo(2);
        assertThat(result.metrics().getFailedStages()).isEqualTo(1);
        assertThat(result.metrics().successRate()).isCloseTo(2.0 / 3.0, Offset.offset(0.001));
    }

    @Test
    void metrics_fullSuccessGivesFullSuccessRateAndZeroFailures() {
        Stage a = Stage.builder("a").agent(ctx -> StageOutput.of("fine")).build();
        Stage b = Stage.builder("b").dependsOn("a").agent(ctx -> StageOutput.of("fine")).build();
        WorkflowDefinition workflow = WorkflowDefinition.of(a, b);

        WorkflowResult result = engine(workflow).run(new WorkflowContext());

        assertThat(result.metrics().successRate()).isCloseTo(1.0, Offset.offset(0.001));
        assertThat(result.metrics().getFailedStages()).isZero();
        assertThat(result.metrics().getRetryCount()).isZero();
        assertThat(result.metrics().getRollbackCount()).isZero();
    }

    @Test
    void metrics_endToEndLatencyIsNonNegativeAfterRun() {
        Stage a = Stage.builder("a").agent(ctx -> StageOutput.of("fine")).build();
        WorkflowDefinition workflow = WorkflowDefinition.of(a);

        WorkflowResult result = engine(workflow).run(new WorkflowContext());

        assertThat(result.metrics().endToEndLatency().isNegative()).isFalse();
    }
}
