package com.example.orchestration.demo;

/**
 * Parameters distinguishing the three demo scenarios. The workflow *shape* is
 * identical for all three (see {@link SdlcWorkflowFactory}) -- only the
 * simulated conditions differ, which is itself part of the point: the same
 * governed pipeline handles a clean run, a recoverable hiccup, an ambiguous
 * requirement, and an outright policy rejection without being rewritten per
 * scenario.
 */
public record ScenarioConfig(
        String scenarioName,
        String rawRequirement,
        boolean injectTransientIntegrationFailure,
        boolean injectReplanAfterIntegration,
        boolean injectTestGenerationFallback,
        boolean injectReleasePolicyDenial
) {
}
