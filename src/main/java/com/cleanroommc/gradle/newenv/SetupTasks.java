package com.cleanroommc.gradle.newenv;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

public final class SetupTasks {

    public static final String GROUP_NAME = "Cleanroom Setup";

    public static TaskProvider<DefaultTask> ROOT;

    public static void init(Project project) {
//        ROOT = Tasks.task(project, GROUP_NAME, "setup");
    }

    private SetupTasks() { }

}
