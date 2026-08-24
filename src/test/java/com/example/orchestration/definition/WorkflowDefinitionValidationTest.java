package com.example.orchestration.definition;

import com.example.orchestration.Stage;
import com.example.orchestration.StageOutput;
import com.example.orchestration.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers {@link WorkflowDefinition}'s construction-time validation: a
 * malformed graph (a cycle, or a dependency on a stage that doesn't exist)
 * fails fast when the workflow is built, rather than deadlocking or silently
 * misbehaving at run time.
 */
class WorkflowDefinitionValidationTest {

    @Test
    void cyclicGraph_isRejectedAtDefinitionTime() {
        Stage a = Stage.builder("a").dependsOn("b").agent(ctx -> StageOutput.of("x")).build();
        Stage b = Stage.builder("b").dependsOn("a").agent(ctx -> StageOutput.of("y")).build();

        assertThrows(IllegalArgumentException.class, () -> WorkflowDefinition.of(a, b));
    }

    @Test
    void unknownDependency_isRejectedAtDefinitionTime() {
        Stage a = Stage.builder("a").dependsOn("does-not-exist").agent(ctx -> StageOutput.of("x")).build();

        assertThrows(IllegalArgumentException.class, () -> WorkflowDefinition.of(a));
    }

    @Test
    void duplicateStageId_isRejectedAtDefinitionTime() {
        Stage a1 = Stage.builder("a").agent(ctx -> StageOutput.of("x")).build();
        Stage a2 = Stage.builder("a").agent(ctx -> StageOutput.of("y")).build();

        assertThrows(IllegalArgumentException.class, () -> WorkflowDefinition.of(a1, a2));
    }

    @Test
    void transitiveDependents_areComputedCorrectlyForADiamondGraph() {
        Stage a = Stage.builder("a").agent(ctx -> StageOutput.of("x")).build();
        Stage b = Stage.builder("b").dependsOn("a").agent(ctx -> StageOutput.of("x")).build();
        Stage c = Stage.builder("c").dependsOn("a").agent(ctx -> StageOutput.of("x")).build();
        Stage d = Stage.builder("d").dependsOn("b", "c").agent(ctx -> StageOutput.of("x")).build();
        WorkflowDefinition workflow = WorkflowDefinition.of(a, b, c, d);

        assertThat(workflow.getTransitiveDependents("a")).containsExactlyInAnyOrder("b", "c", "d");
        assertThat(workflow.getTransitiveDependents("d")).isEmpty();
    }
}
