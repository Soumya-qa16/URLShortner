package com.example.orchestration.demo;

import com.example.orchestration.AutoApprovingApprovalPort;
import com.example.orchestration.OrchestrationEngine;
import com.example.orchestration.WorkflowContext;
import com.example.orchestration.WorkflowDefinition;
import com.example.orchestration.WorkflowResult;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs all three demo scenarios end to end and prints, for each: final stage
 * statuses, reliability metrics, and the full audit trail.
 *
 * <p>Run directly with:
 * <pre>
 * mvn compile exec:java -Dexec.mainClass=com.example.orchestration.demo.OrchestrationDemo
 * </pre>
 * (requires the exec-maven-plugin, or run the class directly from your IDE --
 * this is a standalone entry point, independent of the Spring Boot application,
 * so it will not start the web server or touch Postgres/Redis.)
 */
public final class OrchestrationDemo {

    public static void main(String[] args) {
        List<ScenarioConfig> scenarios = List.of(
                new ScenarioConfig(
                        "BROWNFIELD -- fix inconsistent exception handling in UrlController",
                        "getAnalytics returns a 500 for an unknown short key instead of a clean 4xx",
                        true, false, false, false),
                new ScenarioConfig(
                        "AMBIGUOUS -- add custom alias + expiry, default TTL unspecified",
                        "Let users pick a custom alias and an optional expiry for a short link "
                                + "(default behavior left unspecified by the requester)",
                        false, true, false, false),
                new ScenarioConfig(
                        "GREENFIELD -- bulk URL shortening endpoint",
                        "Accept a batch of URLs and return shortened links for all of them in one call",
                        false, false, true, true)
        );

        for (ScenarioConfig cfg : scenarios) {
            runScenario(cfg);
        }
    }

    private static void runScenario(ScenarioConfig cfg) {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            WorkflowDefinition workflow = SdlcWorkflowFactory.build(cfg);
            WorkflowContext context = new WorkflowContext();
            OrchestrationEngine engine = new OrchestrationEngine(
                    workflow,
                    new AutoApprovingApprovalPort(),
                    new ReleaseReadinessPolicyGuard(cfg.injectReleasePolicyDenial()),
                    executor);

            System.out.println("=".repeat(100));
            System.out.println("SCENARIO: " + cfg.scenarioName());
            System.out.println("Requirement: " + cfg.rawRequirement());
            System.out.println("=".repeat(100));

            WorkflowResult result = engine.run(context);

            System.out.println("\n-- Final stage statuses --");
            result.statuses().forEach((id, status) -> System.out.printf("  %-24s %s%n", id, status));

            System.out.println("\n-- Metrics --");
            System.out.println(result.metrics().renderSummary());

            System.out.println("\n-- Audit trail --");
            System.out.println(context.getAuditLog().renderTrail());
        } finally {
            executor.shutdown();
        }
    }
}
