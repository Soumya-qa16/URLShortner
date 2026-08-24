package com.example.orchestration;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A single node in the workflow's dependency graph: what it depends on, how
 * risky it is, whether it needs human sign-off, its entry/exit gates, its
 * retry/fallback behavior, and how to undo it (and anything sharing its
 * {@code rollbackGroup}) if it fails unrecoverably.
 */
public final class Stage {

    private final String id;
    private final String name;
    private final Set<String> dependsOn;
    private final RiskLevel riskLevel;
    private final boolean requiresApproval;
    private final Gate entryGate;
    private final Gate exitGate;
    private final StageAgent agent;
    private final StageAgent fallback;
    private final RetryPolicy retryPolicy;
    private final String rollbackGroup;
    private final Consumer<WorkflowContext> rollbackAction;

    private Stage(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.name = b.name != null ? b.name : b.id;
        this.dependsOn = Set.copyOf(b.dependsOn);
        this.riskLevel = b.riskLevel;
        this.requiresApproval = b.requiresApproval;
        this.entryGate = b.entryGate != null ? b.entryGate : Gate.alwaysPass();
        this.exitGate = b.exitGate != null ? b.exitGate : Gate.alwaysPass();
        this.agent = Objects.requireNonNull(b.agent, "agent (stage '" + b.id + "' has no agent)");
        this.fallback = b.fallback;
        this.retryPolicy = b.retryPolicy != null ? b.retryPolicy : RetryPolicy.none();
        this.rollbackGroup = b.rollbackGroup != null ? b.rollbackGroup : b.id;
        this.rollbackAction = b.rollbackAction;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<String> getDependsOn() {
        return dependsOn;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public Gate getEntryGate() {
        return entryGate;
    }

    public Gate getExitGate() {
        return exitGate;
    }

    public StageAgent getAgent() {
        return agent;
    }

    public StageAgent getFallback() {
        return fallback;
    }

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public String getRollbackGroup() {
        return rollbackGroup;
    }

    public Consumer<WorkflowContext> getRollbackAction() {
        return rollbackAction;
    }

    @Override
    public String toString() {
        return "Stage{" + id + "}";
    }

    public static final class Builder {
        private final String id;
        private String name;
        private Set<String> dependsOn = Set.of();
        private RiskLevel riskLevel = RiskLevel.LOW;
        private boolean requiresApproval = false;
        private Gate entryGate;
        private Gate exitGate;
        private StageAgent agent;
        private StageAgent fallback;
        private RetryPolicy retryPolicy;
        private String rollbackGroup;
        private Consumer<WorkflowContext> rollbackAction;

        private Builder(String id) {
            this.id = id;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder dependsOn(String... ids) {
            this.dependsOn = Set.of(ids);
            return this;
        }

        public Builder risk(RiskLevel r) {
            this.riskLevel = r;
            return this;
        }

        public Builder requiresApproval(boolean v) {
            this.requiresApproval = v;
            return this;
        }

        public Builder entryGate(Gate g) {
            this.entryGate = g;
            return this;
        }

        public Builder exitGate(Gate g) {
            this.exitGate = g;
            return this;
        }

        public Builder agent(StageAgent a) {
            this.agent = a;
            return this;
        }

        public Builder fallback(StageAgent f) {
            this.fallback = f;
            return this;
        }

        public Builder retryPolicy(RetryPolicy p) {
            this.retryPolicy = p;
            return this;
        }

        public Builder rollbackGroup(String g) {
            this.rollbackGroup = g;
            return this;
        }

        public Builder onRollback(Consumer<WorkflowContext> action) {
            this.rollbackAction = action;
            return this;
        }

        public Stage build() {
            return new Stage(this);
        }
    }
}
