package com.cleanroommc.gradle.api.ext;

import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.util.CleanroomProblems;
import com.cleanroommc.gradle.env.VanillaTasks;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.problems.Problems;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.util.regex.Pattern;

/**
 * A named, independently runnable vanilla Minecraft environment.
 */
public abstract class VanillaEnvironment implements Named {

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final String name;

    private VanillaTasks tasks;

    @Inject
    public VanillaEnvironment(String name) {
        this.name = name;
        this.getVersion().convention(name);
    }

    @Override
    public String getName() {
        return this.name;
    }

    public abstract Property<String> getVersion();

    public abstract Property<Integer> getJavaVersion();

    @Inject
    protected abstract Problems getProblems();

    public void client(Action<? super RunMinecraft> action) {
        tasks().runVanillaClient.configure(action);
    }

    public void server(Action<? super RunMinecraft> action) {
        tasks().runVanillaServer.configure(action);
    }

    public TaskProvider<RunMinecraft> getRunClient() {
        return tasks().runVanillaClient;
    }

    public TaskProvider<RunMinecraft> getRunServer() {
        return tasks().runVanillaServer;
    }

    public void register(Project project, CachesExtension caches, MinecraftExtension minecraft) {
        if (this.tasks != null) {
            return;
        }
        if (!VALID_NAME.matcher(this.name).matches()) {
            var message = "Invalid vanilla environment name '" + this.name + "'.";
            throw CleanroomProblems.throwing(getProblems(), new InvalidUserDataException(message),
                    CleanroomProblems.INVALID_VANILLA_ENVIRONMENT, message,
                    "Use letters, numbers, dots, underscores, or hyphens, starting with a letter or number.");
        }
        var runTask = "run" + VanillaTasks.taskSuffix(this.name) + "Client";
        if (project.getTasks().getNames().contains(runTask)) {
            var message = "Vanilla environment '" + this.name + "' would create the existing task '" + runTask + "'.";
            throw CleanroomProblems.throwing(getProblems(), new InvalidUserDataException(message),
                    CleanroomProblems.INVALID_VANILLA_ENVIRONMENT, message, "Choose a different vanilla environment name.");
        }
        this.tasks = new VanillaTasks(project, caches, minecraft, this);
    }

    private VanillaTasks tasks() {
        if (this.tasks == null) {
            throw new IllegalStateException("Vanilla environment '" + this.name + "' has not been registered yet.");
        }
        return this.tasks;
    }

}
