package com.cleanroommc.gradle.api.task;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Toggleable cleanup of intermediate jars/files that exist only to feed a later task.
 *
 * <p>When discard is enabled, a {@link Delete} task runs after the last scheduled consumer and
 * removes those files from the project-local cache. The shared Gradle Home cache is never touched.
 *
 * <p>Register discard edges next to the pipeline that produces the file. Consumers that are not
 * in the current task graph do not hold the delete back.
 */
public final class IntermediateProcessor {

    public static final String EXTENSION_NAME = "cleanroomIntermediates";

    public static IntermediateProcessor of(Project project) {
        return project.getExtensions().getByType(IntermediateProcessor.class);
    }

    private final TaskContainer tasks;
    private final Provider<Boolean> enabled;

    public IntermediateProcessor(TaskContainer tasks, Provider<Boolean> discardIntermediates) {
        this.tasks = tasks;
        this.enabled = discardIntermediates;
    }

    /**
     * Deletes {@code files} after {@code consumer} has run (or been skipped as up-to-date).
     * No-op at execution time when discard is disabled.
     */
    public TaskProvider<Delete> discardAfter(TaskProvider<?> consumer, Object... files) {
        return discardAfterAll(consumer.getName() + "Intermediates", List.of(consumer), files);
    }

    /**
     * Deletes {@code files} after every scheduled consumer in {@code consumers} has finished.
     */
    public TaskProvider<Delete> discardAfterAll(String name, Collection<? extends TaskProvider<?>> consumers, Object... files) {
        var enabled = this.enabled;
        var consumerNames = consumers.stream().map(TaskProvider::getName).collect(Collectors.joining(", "));
        var discard = this.tasks.register(name, Delete.class);
        discard.configure(task -> {
            task.setDescription("Deletes intermediate pipeline files after " + consumerNames + ".");
            task.onlyIf("discard intermediates is enabled", $ -> enabled.get());
            task.delete(files);
        });
        after(discard, consumers);
        return discard;
    }

    /**
     * Adds more consumers that must finish before an existing discard task runs.
     */
    public void after(TaskProvider<Delete> discard, TaskProvider<?>... consumers) {
        after(discard, Arrays.asList(consumers));
    }

    public void after(TaskProvider<Delete> discard, Collection<? extends TaskProvider<?>> consumers) {
        discard.configure(task -> {
            for (var consumer : consumers) {
                task.mustRunAfter(consumer);
            }
        });
        for (var consumer : consumers) {
            consumer.configure((Task task) -> task.finalizedBy(discard));
        }
    }

}
