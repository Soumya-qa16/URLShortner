package com.example.orchestration.approval;

import com.example.orchestration.ApprovalDecision;
import com.example.orchestration.ApprovalPort;
import com.example.orchestration.AutoApprovingApprovalPort;
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
 * Covers the human-in-the-loop approval checkpoint: a stage marked
 * {@code requiresApproval} pauses for an {@link ApprovalPort} decision, and a
 * rejection fails the stage (and skips its downstream) exactly like any other
 * unrecoverable failure -- also verifies {@link AutoApprovingApprovalPort},
 * the demo/test stand-in, actually approves.
 */
class OrchestrationApprovalTest {

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
    void approvalRejection_failsStageAndSkipsDownstream() {
        Stage highImpact = Stage.builder("deploy")
                .requiresApproval(true)
                .agent(ctx -> StageOutput.of("should never actually run"))
                .build();
        Stage after = Stage.builder("after").dependsOn("deploy")
                .agent(ctx -> StageOutput.of("should never run")).build();
        WorkflowDefinition workflow = WorkflowDefinition.of(highImpact, after);
        ApprovalPort rejectEverything = (stage, ctx) -> new ApprovalDecision(false, "reviewer", "not ready");
        OrchestrationEngine engine = new OrchestrationEngine(
                workflow, rejectEverything, PolicyGuard.allowAll(), executor);

        WorkflowResult result = engine.run(new WorkflowContext());

        assertThat(result.statuses().get("deploy")).isEqualTo(StageStatus.FAILED);
        assertThat(result.statuses().get("after")).isEqualTo(StageStatus.SKIPPED_UPSTREAM_FAILURE);
        assertThat(result.metrics().getApprovalCount()).isEqualTo(1);
    }

    @Test
    void approvalGranted_allowsStageToCompleteAndRunsDownstream() {
        Stage highImpact = Stage.builder("deploy")
                .requiresApproval(true)
                .agent(ctx -> StageOutput.of("deployed"))
                .build();
        Stage after = Stage.builder("after").dependsOn("deploy")
                .agent(ctx -> StageOutput.of("post-deploy step")).build();
        WorkflowDefinition workflow = WorkflowDefinition.of(highImpact, after);
        ApprovalPort approveEverything = (stage, ctx) ->
                new ApprovalDecision(true, "reviewer", "looks good");
        OrchestrationEngine engine = new OrchestrationEngine(
                workflow, approveEverything, PolicyGuard.allowAll(), executor);

        WorkflowResult result = engine.run(new WorkflowContext());

        assertThat(result.statuses().get("deploy")).isEqualTo(StageStatus.COMPLETED);
        assertThat(result.statuses().get("after")).isEqualTo(StageStatus.COMPLETED);
        assertThat(result.metrics().getApprovalCount()).isEqualTo(1);
    }

    @Test
    void autoApprovingApprovalPort_alwaysApproves() {
        Stage highImpact = Stage.builder("deploy")
                .requiresApproval(true)
                .agent(ctx -> StageOutput.of("deployed"))
                .build();
        WorkflowDefinition workflow = WorkflowDefinition.of(highImpact);
        OrchestrationEngine engine = new OrchestrationEngine(
                workflow, new AutoApprovingApprovalPort(), PolicyGuard.allowAll(), executor);

        WorkflowResult result = engine.run(new WorkflowContext());

        assertThat(result.statuses().get("deploy")).isEqualTo(StageStatus.COMPLETED);
    }
}
