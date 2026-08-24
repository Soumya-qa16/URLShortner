package com.example.orchestration;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * An explicit, validated dependency graph of {@link Stage}s. Validates at
 * construction time that every dependency exists and that the graph has no
 * cycles, so a malformed workflow fails fast at definition time rather than
 * deadlocking at runtime.
 */
public final class WorkflowDefinition {

    private final Map<String, Stage> stages;
    private final Map<String, Set<String>> dependents;

    private WorkflowDefinition(Map<String, Stage> stages, Map<String, Set<String>> dependents) {
        this.stages = stages;
        this.dependents = dependents;
    }

    public static WorkflowDefinition of(Stage... stageArr) {
        Map<String, Stage> map = new LinkedHashMap<>();
        for (Stage s : stageArr) {
            if (map.putIfAbsent(s.getId(), s) != null) {
                throw new IllegalArgumentException("Duplicate stage id: " + s.getId());
            }
        }
        for (Stage s : map.values()) {
            for (String dep : s.getDependsOn()) {
                if (!map.containsKey(dep)) {
                    throw new IllegalArgumentException(
                            "Stage '" + s.getId() + "' depends on unknown stage '" + dep + "'");
                }
            }
        }
        detectCycles(map);

        Map<String, Set<String>> dependents = new LinkedHashMap<>();
        for (String id : map.keySet()) {
            dependents.put(id, new LinkedHashSet<>());
        }
        for (Stage s : map.values()) {
            for (String dep : s.getDependsOn()) {
                dependents.get(dep).add(s.getId());
            }
        }
        return new WorkflowDefinition(Map.copyOf(map), Map.copyOf(dependents));
    }

    private static void detectCycles(Map<String, Stage> map) {
        Set<String> visited = new HashSet<>();
        Set<String> stack = new HashSet<>();
        for (String id : map.keySet()) {
            if (hasCycle(id, map, visited, stack)) {
                throw new IllegalArgumentException(
                        "Cycle detected in workflow graph involving stage: " + id);
            }
        }
    }

    private static boolean hasCycle(String id, Map<String, Stage> map, Set<String> visited, Set<String> stack) {
        if (stack.contains(id)) {
            return true;
        }
        if (visited.contains(id)) {
            return false;
        }
        visited.add(id);
        stack.add(id);
        for (String dep : map.get(id).getDependsOn()) {
            if (hasCycle(dep, map, visited, stack)) {
                return true;
            }
        }
        stack.remove(id);
        return false;
    }

    public Collection<Stage> getStages() {
        return stages.values();
    }

    public Stage getStage(String id) {
        return stages.get(id);
    }

    public Set<String> getDependents(String id) {
        return dependents.getOrDefault(id, Set.of());
    }

    /** All stages that depend on {@code id}, directly or transitively. */
    public Set<String> getTransitiveDependents(String id) {
        Set<String> result = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(getDependents(id));
        while (!queue.isEmpty()) {
            String next = queue.poll();
            if (result.add(next)) {
                queue.addAll(getDependents(next));
            }
        }
        return result;
    }
}
