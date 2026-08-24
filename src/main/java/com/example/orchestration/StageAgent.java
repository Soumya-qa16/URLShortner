package com.example.orchestration;

/**
 * The unit of "agentic execution" for one stage. In a production system this is
 * where a call to an LLM (Claude/Copilot/etc.) or a real build/test/deploy tool
 * would live; this engine is deliberately agnostic to what's inside it -- it only
 * needs the success/failure contract below to apply retries, fallback, gates,
 * and governance uniformly across every stage.
 */
@FunctionalInterface
public interface StageAgent {
    StageOutput execute(WorkflowContext context) throws StageExecutionException;
}
