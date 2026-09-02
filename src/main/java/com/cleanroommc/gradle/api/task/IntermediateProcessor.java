package com.cleanroommc.gradle.api.task;

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Toggleable cleanup of intermediate jars/files that exist only to feed a later task.
 *
 * <p>Each edge is a {@link Delete} task. Consumers {@code finalizedBy} it, and it
 * {@code mustRunAfter} those consumers, so Gradle deletes the files after the last requested
 * consumer finishes. Consumers that were not requested stay out of the graph. Only the
 * project-local cache is ever touched, the shared Gradle Home cache is not.
 *
 * <p>Register discard edges next to the pipeline that produces the file.
 */
public abstract class IntermediateProcessor {

    public static final String EXTENSION_NAME = "cleanroomIntermediates";

    public static IntermediateProcessor of(Project project) {
        return project.getExtensions().getByType(IntermediateProcessor.class);
    }

    private final TaskContainer tasks;

    public abstract Property<Boolean> getDiscardIntermediates();

    @Inject
    public IntermediateProcessor(Project project) {
        this.tasks = project.getTasks();
    }

    /**
     * Deletes {@code files} after {@code consumer} has run. Skipped when discard is disabled.
     */
    public Discard discardAfter(TaskProvider<?> consumer, Object... files) {
        return discardAfterAll("discard" + StringUtils.capitalize(consumer.getName()) + "Intermediates",
                List.of(consumer), files);
    }

    /**
     * Deletes {@code files} after every requested consumer has run.
     */
    public Discard discardAfterAll(Collection<? extends TaskProvider<?>> consumers, Object... files) {
        var consumerNames = consumers.stream().map(TaskProvider::getName).toList();
        var taskName = "discard" + consumerNames.stream()
                .map(StringUtils::capitalize)
                .collect(Collectors.joining("And")) + "Intermediates";
        return discardAfterAll(taskName, consumers, files);
    }

    /**
     * Deletes {@code files} after every requested consumer has run, using the supplied stable task name.
     */
    public Discard discardAfterAll(String taskName, Collection<? extends TaskProvider<?>> consumers, Object... files) {
        var enabled = getDiscardIntermediates();
        var consumerNames = consumers.stream().map(TaskProvider::getName).collect(Collectors.joining(", "));
        var discardTask = this.tasks.register(taskName, Delete.class);
        discardTask.configure(task -> {
            task.setDescription("Deletes intermediates after " + consumerNames + ".");
            task.delete(files);
            task.onlyIf("discardIntermediates", spec -> enabled.get());
        });
        var discard = new Discard(discardTask);
        after(discard, consumers);
        return discard;
    }

    /**
     * Adds more consumers that have to run before {@code discard} deletes its files.
     */
    public void after(Discard discard, TaskProvider<?>... consumers) {
        after(discard, Arrays.asList(consumers));
    }

    public void after(Discard discard, Collection<? extends TaskProvider<?>> consumers) {
        for (var consumer : consumers) {
            consumer.configure(task -> task.finalizedBy(discard.task));
            discard.task.configure(task -> task.mustRunAfter(consumer));
        }
    }

    /**
     * A discard edge whose files are deleted after its requested consumers finish.
     */
    public static final class Discard {

        private final TaskProvider<Delete> task;

        private Discard(TaskProvider<Delete> task) {
            this.task = task;
        }

    }

}
