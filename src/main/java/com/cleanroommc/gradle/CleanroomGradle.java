package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.task.IntermediateProcessor;
import com.cleanroommc.gradle.api.util.CleanroomProblems;
import com.cleanroommc.gradle.api.util.CloseHttpClientFlowAction;
import com.cleanroommc.gradle.api.util.LwjglNatives;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.env.*;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.problems.Problems;
import org.gradle.api.tasks.Delete;

import javax.inject.Inject;

public abstract class CleanroomGradle implements Plugin<Project> {

    @Inject
    public abstract FlowScope getFlowScope();

    @Inject
    public abstract Problems getProblems();

    @Override
    public void apply(Project project) {
        var implementationVersion = this.getClass().getPackage().getImplementationVersion();
        var pluginVersion = implementationVersion == null ? "development" : implementationVersion;
        project.getLogger().info("Applying CleanroomGradle {}", pluginVersion);

        project.getPlugins().apply("net.minecraftforge.renamer");
        getFlowScope().always(CloseHttpClientFlowAction.class, spec -> {});

        final var maintenanceGroup = "cleanroom maintenance";

        final var cleanroomExtension = Objects.extension(project, "cleanroom", CleanroomExtension.class);
        IntermediateProcessor.register(project, cleanroomExtension);
        project.getPluginManager().withPlugin("base", plugin -> project.getTasks().named("clean", Delete.class)
                .configure(task -> task.delete(cleanroomExtension.getLocalCacheDirectory())));

        LwjglNatives.register(project, cleanroomExtension);

        final var vanillaTasks = new VanillaTasks(project, cleanroomExtension);
        final var maintenanceTasks = new MaintenanceTasks(project, cleanroomExtension, vanillaTasks, pluginVersion);
        final var mcpTasks = new MCPTasks(project, cleanroomExtension, vanillaTasks);
        UserDevTasks.configuration(project);

        project.afterEvaluate(evaluatedProject -> {
            mcpTasks.configurePatchDevelopment(evaluatedProject, cleanroomExtension, vanillaTasks);

            var mode = cleanroomExtension.getMode().get();
            switch (mode) {
                case LOADER -> {
                    mcpTasks.configureLoaderPipeline(evaluatedProject, cleanroomExtension, vanillaTasks);
                    new CleanroomTasks(evaluatedProject, cleanroomExtension, vanillaTasks, mcpTasks);
                    new DistributionTasks(evaluatedProject, cleanroomExtension, vanillaTasks, mcpTasks);
                }
                case USERDEV -> {
                    if (!UserDevTasks.requested(evaluatedProject, cleanroomExtension)) {
                        var message = "USERDEV mode requires cleanroom.version or a dependency in the cleanroomUserdev configuration.";
                        throw CleanroomProblems.throwing(getProblems(), new InvalidUserDataException(message),
                                CleanroomProblems.MISSING_USERDEV, message,
                                "Set cleanroom.version or declare a cleanroomUserdev dependency.");
                    }
                    new UserDevTasks(evaluatedProject, cleanroomExtension, vanillaTasks, mcpTasks);
                }
                case VANILLA -> { }
            }

            maintenanceTasks.configure(mode, evaluatedProject);
        });
    }

}
