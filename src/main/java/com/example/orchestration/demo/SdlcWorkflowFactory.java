package com.example.orchestration.demo;

import com.example.orchestration.Gate;
import com.example.orchestration.GateResult;
import com.example.orchestration.RetryPolicy;
import com.example.orchestration.RiskLevel;
import com.example.orchestration.Stage;
import com.example.orchestration.StageAgent;
import com.example.orchestration.StageExecutionException;
import com.example.orchestration.StageOutput;
import com.example.orchestration.WorkflowContext;
import com.example.orchestration.WorkflowDefinition;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds the concrete 7-stage SDLC workflow used by all three demo scenarios:
 * requirements-analysis -&gt; architecture-design -&gt; [implementation ||
 * test-generation] -&gt; integration-validation -&gt; documentation -&gt;
 * release-readiness.
 *
 * <p>Agent behavior is parameterized by {@link ScenarioConfig} so the same
 * graph shape can demonstrate a clean run, a recoverable retry, ambiguity +
 * re-planning, a fallback path, and a policy-guard rejection -- without three
 * separate one-off graphs.
 */
public final class SdlcWorkflowFactory {

    private SdlcWorkflowFactory() {
    }

    public static WorkflowDefinition build(ScenarioConfig cfg) {

        StageAgent requirementsAgent = ctx -> {
            ctx.putArtifact("rawRequirement", cfg.rawRequirement());
            boolean ambiguous = cfg.rawRequirement().toLowerCase(Locale.ROOT).contains("unspecified");
            ctx.putArtifact("requirementAmbiguous", ambiguous);
            String normalized = ambiguous
                    ? "Normalized with an explicit assumption the requester didn't state: a blank/whitespace "
                    + "custom alias is treated as 'not provided', and the default TTL is 7 days when the "
                    + "caller omits an expiry."
                    : "Normalized requirement: " + cfg.rawRequirement();
            ctx.putArtifact("normalizedRequirement", normalized);
            return new StageOutput(normalized, Map.of("ambiguous", ambiguous));
        };

        StageAgent designAgent = ctx -> {
            boolean secondPass = ctx.getPlanRevision() > 0;
            String decision = secondPass
                    ? "REVISED after integration-test feedback: default TTL confirmed at 7 days AND blank "
                    + "custom alias is now explicitly checked with String.isBlank() before falling back to "
                    + "auto-generation (the first design pass missed this case)."
                    : "Impacted modules identified: UrlService (alias handling, TTL/caching), ShortenRequest "
                    + "(validation). Initial design: treat a null customAlias as absent; TTL defaults are "
                    + "handled in cacheUrl().";
            ctx.putArtifact("designDecision", decision);
            return new StageOutput(decision, Map.of("planRevision", ctx.getPlanRevision()));
        };

        Gate implementationEntryGate = ctx -> ctx.getArtifact("designDecision") != null
                ? GateResult.pass("architecture-design output available")
                : GateResult.fail("architecture-design output missing");

        StageAgent implementationAgent = ctx -> {
            String decision = ctx.getArtifact("designDecision", String.class);
            String result = "Implemented against design: " + decision;
            ctx.putArtifact("implementationSummary", result);
            return new StageOutput(result, Map.of());
        };

        AtomicInteger testGenAttempts = new AtomicInteger(0);
        StageAgent testGenerationAgent = ctx -> {
            int attempt = testGenAttempts.incrementAndGet();
            if (cfg.injectTestGenerationFallback() && attempt <= 2) {
                throw new StageExecutionException(
                        "Automated test-generation agent could not derive edge cases for bulk input "
                                + "validation (attempt " + attempt + ").", true);
            }
            String result = "Generated unit tests for: " + ctx.getArtifact("designDecision");
            ctx.putArtifact("testGenerationSummary", result);
            return new StageOutput(result, Map.of());
        };
        StageAgent testGenerationFallback = ctx -> {
            String result = "Fallback: hand-authored a reduced smoke-test scaffold covering only the "
                    + "critical path; edge-case coverage flagged as a manual follow-up rather than silently "
                    + "skipped.";
            ctx.putArtifact("testGenerationSummary", result);
            return new StageOutput(result, Map.of("usedFallback", true));
        };

        AtomicInteger integrationAttempts = new AtomicInteger(0);
        AtomicBoolean replanTriggered = new AtomicBoolean(false);
        StageAgent integrationAgent = ctx -> {
            int attempt = integrationAttempts.incrementAndGet();
            if (cfg.injectTransientIntegrationFailure() && attempt == 1) {
                throw new StageExecutionException(
                        "mvn test reported a transient failure (flaky CI environment on attempt 1).", true);
            }
            if (cfg.injectReplanAfterIntegration() && replanTriggered.compareAndSet(false, true)) {
                ctx.markStale("architecture-design");
                return new StageOutput(
                        "Tests revealed the initial design's blank-alias handling was underspecified; "
                                + "flagging architecture-design as stale so it can be revised.", Map.of());
            }
            String result = "All tests passed against: " + ctx.getArtifact("implementationSummary");
            ctx.putArtifact("integrationSummary", result);
            return new StageOutput(result, Map.of());
        };

        StageAgent documentationAgent = ctx -> {
            String summary = "Documentation updated to reflect: " + ctx.getArtifact("integrationSummary");
            ctx.putArtifact("documentationSummary", summary);
            ctx.putArtifact("documentationPublished", true);
            return new StageOutput(summary, Map.of());
        };

        StageAgent releaseAgent = ctx -> {
            String summary = "Release readiness confirmed; artifact set complete for: "
                    + ctx.getArtifact("normalizedRequirement");
            return new StageOutput(summary, Map.of());
        };

        Stage requirementsStage = Stage.builder("requirements-analysis")
                .name("Requirements Analysis")
                .risk(RiskLevel.LOW)
                .agent(requirementsAgent)
                .build();

        Stage designStage = Stage.builder("architecture-design")
                .name("Architecture & Design")
                .dependsOn("requirements-analysis")
                .risk(RiskLevel.MEDIUM)
                .agent(designAgent)
                .build();

        Stage implementationStage = Stage.builder("implementation")
                .name("Implementation")
                .dependsOn("architecture-design")
                .risk(RiskLevel.MEDIUM)
                .entryGate(implementationEntryGate)
                .agent(implementationAgent)
                .build();

        Stage testGenStage = Stage.builder("test-generation")
                .name("Test Generation")
                .dependsOn("architecture-design")
                .risk(RiskLevel.MEDIUM)
                .retryPolicy(RetryPolicy.of(2, Duration.ofMillis(50)))
                .agent(testGenerationAgent)
                .fallback(testGenerationFallback)
                .build();

        Stage integrationStage = Stage.builder("integration-validation")
                .name("Integration Validation")
                .dependsOn("implementation", "test-generation")
                .risk(RiskLevel.MEDIUM)
                .retryPolicy(RetryPolicy.of(2, Duration.ofMillis(50)))
                .agent(integrationAgent)
                .build();

        Stage documentationStage = Stage.builder("documentation")
                .name("Documentation")
                .dependsOn("integration-validation")
                .risk(RiskLevel.LOW)
                .rollbackGroup("release-package")
                .onRollback(ctx -> ctx.putArtifact("documentationPublished", false))
                .agent(documentationAgent)
                .build();

        Stage releaseStage = Stage.builder("release-readiness")
                .name("Release Readiness")
                .dependsOn("documentation")
                .risk(RiskLevel.HIGH)
                .requiresApproval(true)
                .rollbackGroup("release-package")
                .agent(releaseAgent)
                .build();

        return WorkflowDefinition.of(requirementsStage, designStage, implementationStage,
                testGenStage, integrationStage, documentationStage, releaseStage);
    }
}
