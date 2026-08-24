/**
 * A generic, reusable agentic-orchestration engine for coordinating multi-stage
 * SDLC workflows (requirements -&gt; design -&gt; implementation -&gt; testing -&gt;
 * documentation -&gt; release) with explicit dependency graphs, parallel execution,
 * human approval checkpoints, bounded retries/fallback/rollback, policy guardrails,
 * audit-grade observability, and dynamic re-planning.
 *
 * This package contains no domain-specific (URL-shortener) logic -- see
 * {@link com.example.orchestration.demo} for a concrete SDLC workflow built on
 * top of this engine.
 */
package com.example.orchestration;
