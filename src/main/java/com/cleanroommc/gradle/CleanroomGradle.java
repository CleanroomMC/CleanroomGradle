package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.util.CloseHttpClientFlowAction;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.env.CleanroomTasks;
import com.cleanroommc.gradle.env.DistributionTasks;
import com.cleanroommc.gradle.env.MCPTasks;
import com.cleanroommc.gradle.env.UserDevTasks;
import com.cleanroommc.gradle.env.VanillaTasks;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.flow.FlowScope;

import javax.inject.Inject;

public abstract class CleanroomGradle implements Plugin<Project> {

    @Inject
    public abstract FlowScope getFlowScope();

    @Override
    public void apply(Project project) {
        project.getLogger().lifecycle("Running CleanroomGradle v" + this.getClass().getPackage().getImplementationVersion());

        project.getPlugins().apply("net.minecraftforge.renamer");
        getFlowScope().always(CloseHttpClientFlowAction.class, spec -> {});

        final var cleanroomExtension = Objects.extension(project, "cleanroom", CleanroomExtension.class);

        final var vanillaTasks = new VanillaTasks(project, cleanroomExtension);
        final var mcpTasks = new MCPTasks(project, cleanroomExtension, vanillaTasks);

        project.afterEvaluate($ -> {
            vanillaTasks.afterEvaluate($, cleanroomExtension);
            mcpTasks.afterEvaluate($, cleanroomExtension, vanillaTasks);

            if (cleanroomExtension.getLoaderProject().get()) {
                new CleanroomTasks($, cleanroomExtension, vanillaTasks, mcpTasks);
                var distTasks = new DistributionTasks($, cleanroomExtension, vanillaTasks, mcpTasks);
                new UserDevTasks($, cleanroomExtension, vanillaTasks, mcpTasks, distTasks);
            }
        });
    }

}
