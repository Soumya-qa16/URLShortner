package com.example.orchestration.scheduling;

import com.example.orchestration.AutoApprovingApprovalPort;
import com.example.orchestration.OrchestrationEngine;
import com.example.orchestration.PolicyGuard;
import com.example.orchestration.Stage;
import com.example.orchestration.StageAgent;
import com.example.orchestration.StageExecutionException;
import com.example.orchestration.StageOutput;
import com.example.orchestration.StageStatus;
import com.example.orchestration.WorkflowContext;
import com.example.orchestration.WorkflowDefinition;
import com.example.orchestration.WorkflowResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the scheduler's core contract: stages run in dependency order,
 * independent stages genuinely execute in parallel, and a stage with multiple
 * dependencies (a join) waits for all of them before starting.
 */
class OrchestrationSchedulingTest {

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
    void linearChain_executesInDependencyOrder() {
        List<String> executionOrder = new CopyOnWriteArrayList<>();
        Stage a = Stage.builder("a").agent(ctx -> {
            executionOrder.add("a");
            return StageOutput.of("a done");
        }).build();
        Stage b = Stage.builder("b").dependsOn("a").agent(ctx -> {
            executionOrder.add("b");
            return StageOutput.of("b done");
        }).build();
        Stage c = Stage.builder("c").dependsOn("b").agent(ctx -> {
            executionOrder.add("c");
            return StageOutput.of("c done");
        }).build();
        WorkflowDefinition workflow = WorkflowDefinition.of(a, b, c);

        WorkflowResult result = engine(workflow).run(new WorkflowContext());

        assertThat(result.isFullSuccess()).isTrue();
        assertThat(executionOrder).containsExactly("a", "b", "c");
    }

    @Test
    void independentStages_actuallyRunConcurrently() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        StageAgent waitForBoth = ctx -> {
            bothStarted.countDown();
            try {
                if (!bothStarted.await(2, TimeUnit.SECONDS)) {
                    throw new StageExecutionException(
                            "Sibling stage never started -- stages ran sequentially, not in parallel.", false);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return StageOutput.of("done");
        };
        Stage root = Stage.builder("root").agent(ctx -> StageOutput.of("root done")).build();
        Stage b = Stage.builder("b").dependsOn("root").agent(waitForBoth).build();
        Stage c = Stage.builder("c").dependsOn("root").agent(waitForBoth).build();
        WorkflowDefinition workflow = WorkflowDefinition.of(root, b, c);

        WorkflowResult result = engine(workflow).run(new WorkflowContext());

        assertThat(result.statuses().get("b")).isEqualTo(StageStatus.COMPLETED);
        assertThat(result.statuses().get("c")).isEqualTo(StageStatus.COMPLETED);
    }

    @Test
    void joinStage_waitsForAllParallelBranchesBeforeStarting() {
        List<String> order = new CopyOnWriteArrayList<>();
        Stage root = Stage.builder("root").agent(ctx -> StageOutput.of("ok")).build();
        Stage left = Stage.builder("left").dependsOn("root").agent(ctx -> {
            sleepQuietly(30);
            order.add("left");
            return StageOutput.of("ok");
        }).build();
        Stage right = Stage.builder("right").dependsOn("root").agent(ctx -> {
            order.add("right");
            return StageOutput.of("ok");
        }).build();
        Stage join = Stage.builder("join").dependsOn("left", "right").agent(ctx -> {
            order.add("join");
            return StageOutput.of("ok");
        }).build();
        WorkflowDefinition workflow = WorkflowDefinition.of(root, left, right, join);

        WorkflowResult result = engine(workflow).run(new WorkflowContext());

        assertThat(result.isFullSuccess()).isTrue();
        // "join" must come after BOTH branches regardless of which finished first.
        assertThat(order.indexOf("join")).isGreaterThan(order.indexOf("left"));
        assertThat(order.indexOf("join")).isGreaterThan(order.indexOf("right"));
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
