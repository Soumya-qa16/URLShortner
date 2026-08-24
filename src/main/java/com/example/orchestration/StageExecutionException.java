package com.example.orchestration;

/**
 * Thrown by a {@link StageAgent} when a stage's work fails. {@code retryable}
 * tells the engine whether it's worth attempting again (e.g. a transient
 * environment hiccup) or whether retrying would just waste attempts on a
 * deterministic failure (e.g. a malformed input that will never parse).
 */
public class StageExecutionException extends Exception {

    private final boolean retryable;

    public StageExecutionException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public StageExecutionException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
