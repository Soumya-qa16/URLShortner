package com.example.orchestration;

import java.time.Duration;

/**
 * Bounded retry configuration for a stage: how many attempts total, and how
 * long to wait between them. {@link #none()} means "try exactly once."
 */
public record RetryPolicy(int maxAttempts, Duration backoff) {

    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO);
    }

    public static RetryPolicy of(int maxAttempts, Duration backoff) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        return new RetryPolicy(maxAttempts, backoff == null ? Duration.ZERO : backoff);
    }
}
