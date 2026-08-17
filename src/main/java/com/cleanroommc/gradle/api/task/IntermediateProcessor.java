package com.cleanroommc.gradle.api.task;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskProvider;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Toggleable cleanup of intermediate jars/files that exist only to feed a later task.
 *
 * <p>When {@link CleanroomExtension#getDiscardIntermediates()} is {@code true},
 * a {@link Delete} task runs after the last scheduled consumer and removes those files from
 * {@link CleanroomExtension#getLocalCacheDirectory()}. The shared Gradle Home cache is never touched.
 *
 * <p>Defaults `false` for a loader workspace and `true` for userdev.
 * Override with {@code cleanroom.discardIntermediates=true|false} or {@code cleanroom { discardIntermediates = true }}.
 */
public final class IntermediateProcessor {

    public static final String EXTENSION_NAME = "cleanroomIntermediateProcessor";

    public static IntermediateProcessor of(Project project) {
        return (IntermediateProcessor) project.getExtensions().getByName(EXTENSION_NAME);
    }

    public static IntermediateProcessor register(Project project, CleanroomExtension ext) {
        var processor = new IntermediateProcessor(project, ext);
        project.getExtensions().add(EXTENSION_NAME, processor);
        return processor;
    }

    private final Project project;
    private final CleanroomExtension ext;

    private IntermediateProcessor(Project project, CleanroomExtension ext) {
        this.project = project;
        this.ext = ext;
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
     * Consumers that are not in the current task graph do not hold the delete back.
     */
    public TaskProvider<Delete> discardAfterAll(String name, Collection<? extends TaskProvider<?>> consumers, Object... files) {
        var enabled = this.ext.getDiscardIntermediates();
        var discard = this.project.getTasks().register(name, Delete.class, task -> {
            task.setDescription("Deletes intermediate pipeline files after " + name.replace("Intermediates", "") + ".");
            task.onlyIf("cleanroom.discardIntermediates is enabled", $ -> enabled.get());
            task.delete(files);
        });
        alsoAfter(discard, consumers);
        return discard;
    }

    /** Adds more consumers that must finish before an existing discard task runs. */
    public void alsoAfter(TaskProvider<Delete> discard, TaskProvider<?>... consumers) {
        alsoAfter(discard, Arrays.asList(consumers));
    }

    public void alsoAfter(TaskProvider<Delete> discard, Collection<? extends TaskProvider<?>> consumers) {
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
