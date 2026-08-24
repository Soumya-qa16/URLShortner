package com.example.orchestration;

/**
 * Risk classification for a stage, used to decide whether it needs a human
 * approval checkpoint before it's allowed to run.
 */
public enum RiskLevel {
    LOW, MEDIUM, HIGH
}
