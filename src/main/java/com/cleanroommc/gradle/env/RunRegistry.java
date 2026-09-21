/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.RunConfiguration;
import com.cleanroommc.gradle.api.task.mc.NsightExec;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.util.CleanroomProblems;

import org.apache.commons.lang3.StringUtils;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.problems.Problems;
import org.gradle.api.tasks.TaskProvider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;

public abstract class RunRegistry {

    private static final Set<String> CONDITIONAL_TASKS = Set.of(
            "runClient",
            "runServer",
            "runVanillaClient",
            "runVanillaServer",
            "runCleanroomClient",
            "runCleanroomServer",
            "runCleanroomNsightClient",
            "runSrgClient",
            "runSrgServer",
            "runReobfSrgClient",
            "runReobfSrgServer",
            "runMcpClient",
            "runMcpServer"
    );
    private static final String RUNS_GROUP = "minecraft runs";
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final Project project;
    private final NamedDomainObjectContainer<RunConfiguration> runs;
    private final Map<String, Builtin> builtins = new HashMap<>();

    @Inject
    protected abstract Problems getProblems();

    @Inject
    public RunRegistry(Project project, NamedDomainObjectContainer<RunConfiguration> runs) {
        this.project = project;
        this.runs = runs;
        for (var taskName : CONDITIONAL_TASKS) {
            runs.register(StringUtils.uncapitalize(taskName.substring(3)));
        }
    }

    public static <T extends Task> TaskProvider<T> register(Project project, String name, Class<T> type) {
        var task = project.getTasks().register(name, type);
        var registry = project.getExtensions().getByType(RunRegistry.class);
        registry.builtins.put(name, new Builtin(type, task));
        var runName = StringUtils.uncapitalize(name.substring(3));
        if (!registry.runs.getNames().contains(runName)) {
            registry.runs.register(runName);
        }
        return task;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Task> void configure(Project project, TaskProvider<T> task, Action<? super T> action) {
        var builtin = project.getExtensions().getByType(RunRegistry.class).builtins.get(task.getName());
        builtin.actions.add(value -> action.execute((T) builtin.type.cast(value)));
        task.configure(action);
    }

    public static void derived(Project project, TaskProvider<?> task, Action<TaskProvider<?>> action) {
        project.getExtensions().getByType(RunRegistry.class).builtins.get(task.getName()).consumers.add(action);
    }

    public void configureRuns() {
        // Evaluate lazy declarations to discover inheritance while leaving their tasks unrealized
        var declarations = new LinkedHashMap<String, RunConfiguration>();
        for (var name : this.runs.getNames()) {
            declarations.put(name, this.runs.named(name).get());
        }
        var taskNames = new HashMap<String, String>();
        for (var name : declarations.keySet()) {
            if (!VALID_NAME.matcher(name).matches()) {
                throw invalid("Invalid run name '" + name + "'. Use letters, numbers, dots, underscores or hyphens, starting with a letter or number.");
            }
            var taskName = "run" + StringUtils.capitalize(name);
            var previous = taskNames.put(taskName, name);
            if (previous != null) {
                throw invalid("Runs '" + previous + "' and '" + name + "' both name task '" + taskName + "'.");
            }
        }

        for (var name : declarations.keySet()) {
            configureRun(name, declarations);
        }
    }

    private void configureRun(String name, Map<String, RunConfiguration> declarations) {
        var path = new LinkedHashSet<String>();
        var configurations = new ArrayDeque<RunConfiguration>();
        var ancestors = new ArrayList<Builtin>();
        var types = new LinkedHashSet<Class<? extends Task>>();

        for (var current = name; current != null;) {
            if (!path.add(current)) {
                throw invalid("Run inheritance cycle " + String.join(" -> ", path) + " -> " + current + ".");
            }
            var taskName = "run" + StringUtils.capitalize(current);
            var builtin = this.builtins.get(taskName);
            if (builtin == null && this.project.getTasks().getNames().contains(taskName)) {
                Class<? extends Task> taskType;
                if (this.project.getTasks().withType(RunMinecraft.class).getNames().contains(taskName)) {
                    taskType = RunMinecraft.class;
                } else if (this.project.getTasks().withType(NsightExec.class).getNames().contains(taskName)) {
                    taskType = NsightExec.class;
                } else {
                    throw invalid("Run '" + current + "' conflicts with non-run task '" + taskName + "'.");
                }
                builtin = new Builtin(taskType, this.project.getTasks().named(taskName, taskType));
                this.builtins.put(taskName, builtin);
            }
            if (builtin == null && CONDITIONAL_TASKS.contains(taskName)) {
                return;
            }
            if (builtin != null) {
                types.add(builtin.type);
                if (!current.equals(name)) {
                    ancestors.add(builtin);
                }
            }
            var declaration = declarations.get(current);
            if (declaration == null) {
                if (builtin == null) {
                    throw invalid("Unknown inherited run '" + current + "'. Use a built-in run or declare it in cleanroom.runs.");
                }
                break;
            }
            configurations.addFirst(declaration);
            declaration.getInherit().finalizeValue();
            current = declaration.getInherit().getOrNull();
            if (current == null && builtin == null) {
                types.add(RunMinecraft.class);
            }
        }
        if (types.size() > 1) {
            throw invalid("Runs in inheritance chain '" + String.join(" -> ", path) + "' cannot be combined because their task types differ.");
        }

        var taskName = "run" + StringUtils.capitalize(name);
        var builtin = this.builtins.get(taskName);
        var task = builtin == null ? this.project.getTasks().register(taskName, types.getFirst(), value -> value.setGroup(RUNS_GROUP)) : builtin.task;
        var consumers = new LinkedHashSet<Action<TaskProvider<?>>>();
        for (var ancestor : ancestors) {
            ancestor.actions.forEach(task::configure);
            consumers.addAll(ancestor.consumers);
        }
        for (var configuration : configurations) {
            configuration.getConfigurationActions().forEach(task::configure);
        }
        if (builtin != null) {
            consumers.removeAll(builtin.consumers);
        }
        consumers.forEach(consumer -> consumer.execute(task));
    }

    private RuntimeException invalid(String message) {
        return CleanroomProblems.throwing(
                getProblems(),
                new InvalidUserDataException(message),
                CleanroomProblems.INVALID_RUN,
                message,
                "Check the declarations in cleanroom.runs."
        );
    }

    private record Builtin(Class<? extends Task> type, TaskProvider<?> task, List<Action<? super Task>> actions, Set<Action<TaskProvider<?>>> consumers) {

        private Builtin(Class<? extends Task> type, TaskProvider<?> task) {
            this(type, task, new ArrayList<>(), new LinkedHashSet<>());
        }

    }

}
