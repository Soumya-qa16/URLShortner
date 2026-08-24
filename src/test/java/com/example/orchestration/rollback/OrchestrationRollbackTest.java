package com.example.orchestration.rollback;

import com.example.orchestration.AutoApprovingApprovalPort;
import com.example.orchestration.OrchestrationEngine;
import com.example.orchestration.PolicyGuard;
import com.example.orchestration.RetryPolicy;
import com.example.orchestration.Stage;
import com.example.orchestration.StageExecutionException;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers rollback: when a stage fails unrecoverably (retries and fallback both
 * exhausted), the engine rolls back that stage plus any already-completed
 * sibling sharing its rollback group, and safely skips (rather than crashes)
 * everything downstream of the failure.
 */
class OrchestrationRollbackTest {

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
    void retryAndFallbackBothFail_rollsBackGroupAndSkipsDownstream() {
        AtomicBoolean rolledBack = new AtomicBoolean(false);
        Stage sibling = Stage.builder("sibling")
                .rollbackGroup("txn")
                .onRollback(ctx -> rolledBack.set(true))
                .agent(ctx -> StageOutput.of("sibling completed first"))
                .build();
        Stage failing = Stage.builder("failing")
                .dependsOn("sibling")
                .rollbackGroup("txn")
                .retryPolicy(RetryPolicy.of(2, java.time.Duration.ZERO))
                .agent(ctx -> {
                    throw new StageExecutionException("primary always fails", true);
                })
                .fallback(ctx -> {
                    throw new StageExecutionException("fallback also fails", false);
                })
                .build();
        Stage downstream = Stage.builder("downstream").dependsOn("failing")
                .agent(ctx -> StageOutput.of("should never run")).build();
        WorkflowDefinition workflow = WorkflowDefinition.of(sibling, failing, downstream);

        WorkflowResult result = engine(workflow).run(new WorkflowContext());

        assertThat(result.statuses().get("failing")).isEqualTo(StageStatus.FAILED);
        assertThat(result.statuses().get("sibling")).isEqualTo(StageStatus.ROLLED_BACK);
        assertThat(result.statuses().get("downstream")).isEqualTo(StageStatus.SKIPPED_UPSTREAM_FAILURE);
        assertThat(rolledBack.get()).isTrue();
        assertThat(result.metrics().getRollbackCount()).isGreaterThanOrEqualTo(1);
    }
}
