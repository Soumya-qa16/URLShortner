package com.example.orchestration.fallback;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the fallback path: once a stage's primary agent exhausts its retry
 * budget, the engine invokes the stage's fallback agent (if one is
 * configured) before giving up and failing the stage.
 */
class OrchestrationFallbackTest {

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
    void retriesExhausted_thenFallbackSucceeds() {
        Stage a = Stage.builder("a")
                .retryPolicy(RetryPolicy.of(2, java.time.Duration.ZERO))
                .agent(ctx -> {
                    throw new StageExecutionException("primary agent always fails", true);
                })
                .fallback(ctx -> {
                    ctx.putArtifact("usedFallback", true);
                    return StageOutput.of("fallback result");
                })
                .build();
        WorkflowDefinition workflow = WorkflowDefinition.of(a);
        WorkflowContext context = new WorkflowContext();

        WorkflowResult result = engine(workflow).run(context);

        assertThat(result.statuses().get("a")).isEqualTo(StageStatus.COMPLETED);
        assertThat(context.getArtifact("usedFallback")).isEqualTo(Boolean.TRUE);
        assertThat(result.metrics().getFallbackCount()).isEqualTo(1);
    }
}
