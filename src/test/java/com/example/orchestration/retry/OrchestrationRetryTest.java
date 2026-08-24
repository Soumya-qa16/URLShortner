package com.example.orchestration.retry;

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

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers bounded retry behavior: a retryable failure gets retried up to the
 * stage's {@link RetryPolicy}, a non-retryable failure never retries even with
 * attempts remaining, and each retry is counted in {@link com.example.orchestration.Metrics}.
 */
class OrchestrationRetryTest {

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
    void retryableFailure_succeedsOnSecondAttempt() {
        AtomicInteger attempts = new AtomicInteger(0);
        Stage a = Stage.builder("a")
                .retryPolicy(RetryPolicy.of(3, Duration.ofMillis(5)))
                .agent(ctx -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new StageExecutionException("transient failure", true);
                    }
                    return StageOutput.of("succeeded on attempt " + attempts.get());
                })
                .build();
        WorkflowDefinition workflow = WorkflowDefinition.of(a);

        WorkflowResult result = engine(workflow).run(new WorkflowContext());

        assertThat(result.statuses().get("a")).isEqualTo(StageStatus.COMPLETED);
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(result.metrics().getRetryCount()).isEqualTo(1);
    }

    @Test
    void nonRetryableFailure_doesNotRetryEvenWithAttemptsRemaining() {
        AtomicInteger attempts = new AtomicInteger(0);
        Stage a = Stage.builder("a")
                .retryPolicy(RetryPolicy.of(5, Duration.ZERO))
                .agent(ctx -> {
                    attempts.incrementAndGet();
                    throw new StageExecutionException("deterministic failure, not worth retrying", false);
                })
                .build();
        WorkflowDefinition workflow = WorkflowDefinition.of(a);

        WorkflowResult result = engine(workflow).run(new WorkflowContext());

        assertThat(result.statuses().get("a")).isEqualTo(StageStatus.FAILED);
        assertThat(attempts.get()).isEqualTo(1);
        assertThat(result.metrics().getRetryCount()).isZero();
    }
}
