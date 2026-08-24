/**
 * A concrete 7-stage SDLC workflow (requirements -&gt; design -&gt; [implementation
 * || test-generation] -&gt; integration-validation -&gt; documentation -&gt;
 * release-readiness) built on the {@link com.example.orchestration} engine, and
 * three runnable scenarios demonstrating it:
 *
 * <ul>
 *   <li><b>Brownfield</b> -- the real exception-handling fix made earlier in this
 *       project (see {@code com.example.exception}): parallel execution +
 *       bounded retry recovering a transient integration failure + an approval
 *       checkpoint.</li>
 *   <li><b>Ambiguous</b> -- an underspecified custom-alias/TTL requirement
 *       (mirroring the real {@code ShortenRequest} behavior): ambiguity
 *       detection + dynamic re-planning after integration testing reveals the
 *       initial design assumption was wrong.</li>
 *   <li><b>Greenfield</b> -- a new bulk-shortening endpoint: a fallback path
 *       when test-generation can't derive edge cases, and a hard policy-guard
 *       denial at release-readiness (missing load-test evidence) that triggers
 *       rollback and a safe-stop.</li>
 * </ul>
 *
 * Run {@link com.example.orchestration.demo.OrchestrationDemo#main} to execute
 * all three end to end.
 */
package com.example.orchestration.demo;
