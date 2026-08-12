package com.cleanroommc.gradle.api.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;

public final class Tasks {

    public static TaskProvider<DefaultTask> register(Project project, String name) {
        return register(project, name, DefaultTask.class);
    }

    public static <T extends Task> TaskProvider<T> register(Project project, String name, Class<T> type) {
        return project.getTasks().register(name, type);
    }

    public static <T extends MavenJarExec> TaskProvider<T> tool(Project project, DirectoryProperty localCache,
                                                                String name, Class<T> type, Configuration classpath) {
        var task = register(project, name, type);
        task.configure(exec -> {
            exec.getToolClasspath().from(classpath);
            exec.setWorkingDir(localCache.dir(name));
        });
        return task;
    }

    @SafeVarargs
    public static void group(String group, TaskProvider<? extends Task>... tasks) {
        for (var task : tasks) {
            task.configure(value -> value.setGroup(group));
        }
    }

    public static TaskProvider<Copy> copy(Project project, String name, Object from, Object to) {
        var provider = project.getTasks().register(name, Copy.class);
        provider.configure(task -> {
            task.from(from);
            task.into(to);
        });
        return provider;
    }

    public static TaskProvider<Copy> unzip(Project project, String name, Object from, Object to) {
        var provider = project.getTasks().register(name, Copy.class);
        provider.configure(task -> {
            var archives = project.files(from);
            task.from(archives.getElements().map(files -> files.stream()
                    .map(file -> project.zipTree(file.getAsFile()))
                    .toList()));
            task.into(to);
        });
        return provider;
    }

    public static TaskProvider<Zip> zip(Project project, String name, Object from, Object to) {
        var provider = project.getTasks().register(name, Zip.class);
        provider.configure(task -> {
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);

            task.from(from);
            var file = project.file(to);
            task.getDestinationDirectory().set(file.getParentFile());
            task.getArchiveFileName().set(file.getName());
        });
        return provider;
    }

    private Tasks() { }
}
