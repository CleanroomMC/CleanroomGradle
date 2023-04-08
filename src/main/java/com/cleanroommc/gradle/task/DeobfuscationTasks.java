package com.cleanroommc.gradle.task;

import com.cleanroommc.gradle.task.rename.JarRenamingTask;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

public final class DeobfuscationTasks {

    public static final String DEOBFUSCATION_GROUP = "deobfuscation";

    public static final String NOTCH_TO_SEARGE_CLIENT = "notch2seargeClient";

    private static TaskProvider<JarRenamingTask> notchToSeargeClientDeobfuscationTask;

    public static void create(TaskContainer taskContainer) {
        // notchToSeargeClientDeobfuscationTask = taskContainer.register(NOTCH_TO_SEARGE_CLIENT, JarRenamingTask.class, task -> {

        // });
    }

    private DeobfuscationTasks() { }

}
