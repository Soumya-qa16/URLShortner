package com.example.orchestration.replanning;

import com.example.orchestration.AutoApprovingApprovalPort;
import com.example.orchestration.OrchestrationEngine;
import com.example.orchestration.PolicyGuard;
import com.example.orchestration.Stage;
import com.example.orchestration.StageOutput;
import com.example.orchestration.WorkflowContext;
import com.example.orchestration.WorkflowDefinition;
import com.example.orchestration.WorkflowResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers dynamic re-planning: an agent can call
 * {@link WorkflowContext#markStale} on an already-completed upstream stage,
 * which resets that stage and everything transitively downstream of it back
 * to PENDING under a new plan revision -- while stages outside that subgraph
 * (unrelated branches, and the stage's own already-completed dependencies)
 * are left untouched, preserving decision lineage/context.
 */
class OrchestrationReplanningTest {

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
    void replanning_resetsStageAndDependents_whileLeavingUnrelatedBranchUntouched() {
        AtomicInteger aExecutions = new AtomicInteger(0);
        AtomicBoolean replanTriggered = new AtomicBoolean(false);
        AtomicInteger dExecutions = new AtomicInteger(0);

        Stage a = Stage.builder("a").agent(ctx -> {
            aExecutions.incrementAndGet();
            return StageOutput.of("a completed, revision " + ctx.getPlanRevision());
        }).build();
        Stage b = Stage.builder("b").dependsOn("a").agent(ctx -> {
            if (replanTriggered.compareAndSet(false, true)) {
                ctx.markStale("a");
            }
            return StageOutput.of("b completed");
        }).build();
        Stage c = Stage.builder("c").dependsOn("b").agent(ctx -> StageOutput.of("c completed")).build();
        // D is an independent branch that does NOT depend on A, and must be left alone by the replan.
        Stage d = Stage.builder("d").agent(ctx -> {
            dExecutions.incrementAndGet();
            return StageOutput.of("d completed");
        }).build();
        WorkflowDefinition workflow = WorkflowDefinition.of(a, b, c, d);

        WorkflowResult result = engine(workflow).run(new WorkflowContext());

        assertThat(result.isFullSuccess()).isTrue();
        assertThat(aExecutions.get()).isEqualTo(2); // original pass + one re-plan pass
        assertThat(dExecutions.get()).isEqualTo(1); // never touched by A's re-plan
        assertThat(result.metrics().getReplanCount()).isGreaterThanOrEqualTo(1);
        assertThat(result.context().getPlanRevision()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void replanning_preservesArtifactsFromStagesOutsideTheInvalidatedSubgraph() {
        AtomicBoolean replanTriggered = new AtomicBoolean(false);
        Stage upstream = Stage.builder("upstream").agent(ctx -> {
            ctx.putArtifact("upstreamValue", "computed-once");
            return StageOutput.of("upstream completed");
        }).build();
        Stage design = Stage.builder("design").dependsOn("upstream").agent(ctx ->
                StageOutput.of("design revision " + ctx.getPlanRevision())
        ).build();
        Stage validate = Stage.builder("validate").dependsOn("design").agent(ctx -> {
            if (replanTriggered.compareAndSet(false, true)) {
                ctx.markStale("design");
            }
            return StageOutput.of("validated");
        }).build();
        WorkflowDefinition workflow = WorkflowDefinition.of(upstream, design, validate);

        WorkflowResult result = engine(workflow).run(new WorkflowContext());

        assertThat(result.isFullSuccess()).isTrue();
        // "upstream" is not downstream of "design", so its artifact must survive the re-plan untouched.
        assertThat(result.context().getArtifact("upstreamValue")).isEqualTo("computed-once");
    }
}
