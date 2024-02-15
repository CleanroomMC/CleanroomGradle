package com.cleanroommc.gradle.api.named.task;

import de.undercouch.gradle.tasks.download.Download;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.CopySpec;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;

public final class Tasks {

    public static <T extends Task> TaskProvider<T> of(Project project, String name, Class<T> clazz) {
        return project.getTasks().register(name, clazz);
    }

    public static <T extends Task> TaskProvider<T> with(Project project, String name, Class<T> clazz, Action<T> action) {
        var task = project.getTasks().register(name, clazz);
        task.configure(action);
        return task;
    }

    public static TaskProvider<DefaultTask> withDefault(Project project, String name, Action<DefaultTask> action) {
        return with(project, name, DefaultTask.class, action);
    }

    public static TaskProvider<Copy> withCopy(Project project, String name, Action<Copy> action) {
        return with(project, name, Copy.class, action);
    }

    public static TaskProvider<Copy> unzip(Project project, String name, Configuration from, Object to) {
        return withCopy(project, name, copy -> {
            from.getAsFileTree().forEach(f -> copy.from(project.zipTree(f)));
            copy.into(to);
        });
    }

    public static TaskProvider<Copy> unzip(Project project, String name, Configuration from, Object to, Action<CopySpec> specAction) {
        return withCopy(project, name, copy -> {
            from.getAsFileTree().forEach(f -> copy.from(project.zipTree(f)));
            copy.into(to);
            specAction.execute(copy);
        });
    }

    public static TaskProvider<Copy> unzip(Project project, String name, Object from, Object to) {
        return withCopy(project, name, copy -> {
            copy.from(project.zipTree(from));
            copy.into(to);
        });
    }

    public static TaskProvider<Copy> unzip(Project project, String name, Object from, Object to, Action<CopySpec> specAction) {
        return withCopy(project, name, copy -> {
            copy.from(project.zipTree(from));
            copy.into(to);
            specAction.execute(copy);
        });
    }

    public static TaskProvider<Download> withDownload(Project project, String name, Action<Download> action) {
        return with(project, name, Download.class, action);
    }

    public static TaskProvider<Download> withCheckedDownload(Project project, String name, Action<Download> action, Provider<String> sha1) {
        var task = of(project, name, Download.class);
        task.configure(action);

        return task;
    }

    public static <T extends Task> TaskProvider<T> named(Project project, String taskName, Class<T> type) {
        return project.getTasks().named(taskName, type);
    }

    public static <T extends Task> void configure(Project project, String taskName, Action<? super T> action) {
        project.getTasks().named(taskName).configure((Action<? super Task>) action);
    }

    private Tasks() { }

}
