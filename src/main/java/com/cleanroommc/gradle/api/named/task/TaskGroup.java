package com.cleanroommc.gradle.api.named.task;

import org.gradle.api.Named;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;

import java.util.ArrayList;
import java.util.List;

public class TaskGroup implements Named {

    public static TaskGroup of(String name) {
        return new TaskGroup(name);
    }

    private final String name;
    private final List<TaskProvider<? extends Task>> tasks;

    private TaskGroup(String name) {
        this.name = name;
        tasks = new ArrayList<>();
    }

    public <T extends Task> TaskProvider<T> add(TaskProvider<T> provider) {
        this.tasks.add(provider);
        provider.configure(t -> t.setGroup(name));
        return provider;
    }

    public <T extends Task> TaskProvider<T> get(String name) {
        return (TaskProvider<T>) this.tasks.stream().filter(t -> t.getName().equals(name)).findFirst().get();
    }

    public List<TaskProvider<?>> tasks() {
        return List.copyOf(tasks);
    }

    @Override
    public String getName() {
        return name;
    }

}
