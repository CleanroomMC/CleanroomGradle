package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.ext.DeobfExtension;
import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.task.IntermediateProcessor;
import com.cleanroommc.gradle.api.task.mcp.WriteMappings;
import com.cleanroommc.gradle.api.util.CleanroomProblems;
import com.cleanroommc.gradle.api.util.CloseHttpClientFlowAction;
import com.cleanroommc.gradle.api.util.LwjglNatives;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.env.CleanroomTasks;
import com.cleanroommc.gradle.env.DistributionTasks;
import com.cleanroommc.gradle.env.MCPTasks;
import com.cleanroommc.gradle.env.MaintenanceTasks;
import com.cleanroommc.gradle.env.McpMappings;
import com.cleanroommc.gradle.env.ToolConfigs;
import com.cleanroommc.gradle.env.UserDevTasks;
import com.cleanroommc.gradle.env.VanillaTasks;
import net.minecraftforge.renamer.gradle.RenameJar;
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

        final var ext = Objects.extension(project, "cleanroom", CleanroomExtension.class);
        ToolConfigs.register(project);
        final var deobfExt = Objects.extension(project, "deobf", DeobfExtension.class);

        var intermediates = new IntermediateProcessor(project.getTasks(), ext.getCaches().getDiscardIntermediates());
        project.getExtensions().add(IntermediateProcessor.class, IntermediateProcessor.EXTENSION_NAME, intermediates);

        project.getPluginManager().withPlugin("base", plugin -> project.getTasks().named("clean", Delete.class)
                .configure(task -> task.delete(ext.getCaches().getLocalDirectory())));

        LwjglNatives.register(project, ext.getLoader().getLwjglNativesClassifiers());

        final var vanillaTasks = new VanillaTasks(project, ext.getCaches(), ext.getMinecraft());
        final var maintenanceTasks = new MaintenanceTasks(project, ext.getCaches(), ext.getMappings(), vanillaTasks, pluginVersion);
        UserDevTasks.configuration(project);

        project.afterEvaluate(evaluatedProject -> {
            var mode = ext.getMode().get();
            switch (mode) {
                case LOADER -> {
                    var mappings = new McpMappings(evaluatedProject, ext.getCaches(), ext.getMappings());
                    mappings.configurePatchMappings(ext.getPatches());
                    var mcpTasks = new MCPTasks(evaluatedProject, ext.getCaches(), ext.getMinecraft(), ext.getMappings(),
                            vanillaTasks, mappings, intermediates);
                    mcpTasks.configureLoaderPipeline(evaluatedProject, ext.getCaches(), ext.getLoader(), vanillaTasks, intermediates);
                    mcpTasks.configureInitialPatches(evaluatedProject, ext.getCaches(), ext.getPatches(), vanillaTasks, mappings);
                    new CleanroomTasks(evaluatedProject, ext.getCaches(), ext.getMinecraft(), ext.getLoader(),
                            ext.getPatches(), vanillaTasks, mcpTasks, mappings);
                    var distributionTasks = new DistributionTasks(evaluatedProject, ext.getCaches(), ext.getMinecraft(), ext.getLoader(),
                            vanillaTasks, mappings, intermediates);
                    DeobfExtension.rejectOnCompileClasspath(evaluatedProject, getProblems());
                    deobfExt.getMappings().from(mappings.writeSrg2Mcp.flatMap(WriteMappings::getOutput));
                    deobfExt.getSrgLibraries().from(distributionTasks.reobfJar.flatMap(RenameJar::getOutput), vanillaTasks.vanillaConfig);
                }
                case USERDEV -> {
                    if (!UserDevTasks.requested(evaluatedProject, ext.getUserdev())) {
                        var message = "USERDEV mode requires cleanroom.userdev.version or a dependency in the cleanroomUserdev configuration.";
                        throw CleanroomProblems.throwing(getProblems(), new InvalidUserDataException(message),
                                CleanroomProblems.MISSING_USERDEV, message,
                                "Set cleanroom.userdev.version or declare a cleanroomUserdev dependency.");
                    }
                    var mappings = new McpMappings(evaluatedProject, ext.getCaches(), ext.getMappings());
                    mappings.configurePatchMappings(ext.getPatches());
                    var userDevTasks = new UserDevTasks(evaluatedProject, ext.getCaches(), ext.getMinecraft(), ext.getUserdev(),
                            vanillaTasks, mappings, intermediates);
                    deobfExt.useUserdev(userDevTasks.userdev.get());
                    deobfExt.getSrgLibraries().from(vanillaTasks.vanillaConfig, userDevTasks.userdev);
                }
                case VANILLA -> { }
            }
            deobfExt.wireTransformOrdering(evaluatedProject);

            maintenanceTasks.configure(mode, evaluatedProject);
        });
    }

}
