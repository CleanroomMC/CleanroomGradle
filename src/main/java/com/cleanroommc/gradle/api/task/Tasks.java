package com.cleanroommc.gradle.api.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;

public final class Tasks {

    public static TaskProvider<DefaultTask> of(Project project, String group, String name) {
        return of(project, group, name, DefaultTask.class);
    }

    public static <T extends Task> TaskProvider<T> of(Project project, String group, String name, Class<T> type) {
        var provider = project.getTasks().register(name, type);
        provider.configure(task -> task.setGroup(group));
        return provider;
    }

    public static <T extends Task> TaskProvider<T> named(Project project, String name) {
        return (TaskProvider<T>) project.getTasks().named(name);
    }

    public static TaskProvider<Copy> copy(Project project, String group, String name, Object from, Object to) {
        var provider = project.getTasks().register(name, Copy.class);
        provider.configure(task -> {
            task.setGroup(group);

            task.from(from);
            task.into(to);
        });
        return provider;
    }

    public static TaskProvider<Copy> unzip(Project project, String group, String name, Object from, Object to) {
        var provider = project.getTasks().register(name, Copy.class);
        provider.configure(task -> {
            task.setGroup(group);

            project.files(from).forEach(f -> task.from(project.zipTree(f)));
            task.into(to);
        });
        return provider;
    }

    public static TaskProvider<Zip> zip(Project project, String group, String name, Object from, Object to) {
        var provider = project.getTasks().register(name, Zip.class);
        provider.configure(task -> {
            task.setGroup(group);

            task.from(from);
            var file = project.file(to);
            task.getDestinationDirectory().set(file.getParentFile());
            task.getArchiveFileName().set(file.getName());
        });
        return provider;
    }

    public static TaskProvider<Jar> jar(Project project, String group, String name, Object from, Object to) {
        var provider = project.getTasks().register(name, Jar.class);
        provider.configure(task -> {
            task.setDescription("Assembles a jar archive containing the classes of the '" + name + "' feature.");
            task.setGroup(group);

            task.from(from);
            var file = project.file(to);
            task.getDestinationDirectory().set(file.getParentFile());
            task.getArchiveFileName().set(file.getName());
        });
        return provider;
    }

    private Tasks() { }
}
