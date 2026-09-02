package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.ext.DeobfExtension;
import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.ext.ProjectMode;
import com.cleanroommc.gradle.api.task.IntermediateProcessor;
import com.cleanroommc.gradle.api.task.mcp.WriteMappings;
import com.cleanroommc.gradle.api.util.CloseHttpClientFlowAction;
import com.cleanroommc.gradle.api.util.LwjglNatives;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.api.util.lazy.ProjectCoordinates;
import com.cleanroommc.gradle.env.CleanroomTasks;
import com.cleanroommc.gradle.env.DistributionTasks;
import com.cleanroommc.gradle.env.MCPTasks;
import com.cleanroommc.gradle.env.MaintenanceTasks;
import com.cleanroommc.gradle.env.McpMappings;
import com.cleanroommc.gradle.env.ToolConfigs;
import com.cleanroommc.gradle.env.UserDevTasks;
import com.cleanroommc.gradle.env.VanillaTasks;
import com.cleanroommc.gradle.api.userdev.UserdevAttributes;
import net.minecraftforge.renamer.gradle.RenameJar;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.problems.Problems;
import org.gradle.api.tasks.Delete;

import javax.inject.Inject;
import java.util.ArrayList;

public abstract class CleanroomGradle implements Plugin<Project> {

    @Inject
    public abstract FlowScope getFlowScope();

    @Inject
    public abstract Problems getProblems();

    @Inject
    public abstract SoftwareComponentFactory getSoftwareComponentFactory();

    @Override
    public void apply(Project project) {
        var implementationVersion = this.getClass().getPackage().getImplementationVersion();
        var pluginVersion = implementationVersion == null ? "development" : implementationVersion;
        project.getLogger().info("Applying CleanroomGradle {}", pluginVersion);

        project.getPlugins().apply("net.minecraftforge.renamer");
        getFlowScope().always(CloseHttpClientFlowAction.class, spec -> {});

        final var ext = Objects.extension(project, "cleanroom", CleanroomExtension.class);
        project.getDependencies().getAttributesSchema().attribute(UserdevAttributes.ROLE,
                strategy -> strategy.getDisambiguationRules().add(UserdevAttributes.PreferClasses.class));
        project.getDependencies().getExtensions().add("cleanroomUserdev", new RemovedUserdevDependency());
        ToolConfigs.register(project);
        final var deobfExt = Objects.extension(project, "deobf", DeobfExtension.class);

        // A pipeline is registered when its mode is picked, which is usually before the buildscript sets
        // its coordinates, so anything that names them reads these instead of Project.getGroup/getVersion
        var coordinates = new ProjectCoordinates(project);

        var intermediates = project.getExtensions().create(IntermediateProcessor.EXTENSION_NAME, IntermediateProcessor.class);
        intermediates.getDiscardIntermediates().set(ext.getCaches().getDiscardIntermediates());

        project.getPluginManager().withPlugin("base", plugin -> project.getTasks().named("clean", Delete.class)
                .configure(task -> task.delete(ext.getCaches().getLocalDirectory())));

        LwjglNatives.register(project, ext.getLoader().getLwjglNativesClassifiers());

        // Every pipeline is registered the moment its mode is picked, so a buildscript can go on to
        // configure runClient or universalJar by name and have its own configuration run after the
        // plugin's. Only the task sets gated by an extension property the same cleanroom { } block sets
        // afterwards, and the wiring that has to see every configuration, wait for evaluation.
        var deferred = new ArrayList<Action<Project>>();
        ext.onModeSelected(mode -> {
            var vanillaTasks = new VanillaTasks(project, ext.getCaches(), ext.getMinecraft());
            var maintenanceTasks = new MaintenanceTasks(project, ext.getCaches(), ext.getMappings(), vanillaTasks, pluginVersion);
            switch (mode) {
                case LOADER -> {
                    // Only the loader distribution has a publication of its own; a buildscript that configures
                    // publishing { } in its body applies maven-publish itself, which this apply then no-ops on
                    project.getPlugins().apply("maven-publish");
                    var mappings = new McpMappings(project, ext.getCaches(), ext.getMappings());
                    mappings.configurePatchMappings(ext.getPatches());
                    var mcpTasks = new MCPTasks(project, ext.getCaches(), ext.getMinecraft(), ext.getMappings(),
                            vanillaTasks, mappings, intermediates);
                    mcpTasks.configureLoaderPipeline(project, ext.getCaches(), ext.getLoader(), vanillaTasks, intermediates);
                    new CleanroomTasks(project, coordinates, ext.getCaches(), ext.getMinecraft(), ext.getLoader(),
                            ext.getPatches(), vanillaTasks, mcpTasks, mappings);
                    var distributionTasks = new DistributionTasks(project, coordinates, ext.getCaches(),
                            ext.getMinecraft(), ext.getLoader(), ext.getPatches(), vanillaTasks, mappings, mcpTasks,
                            intermediates, getSoftwareComponentFactory());
                    DeobfExtension.rejectOnCompileClasspath(project, getProblems());
                    deobfExt.getMappings().from(mappings.writeSrg2Mcp.flatMap(WriteMappings::getOutput));
                    deobfExt.getSrgLibraries().from(distributionTasks.reobfJar.flatMap(RenameJar::getOutput), vanillaTasks.vanillaConfig);
                    deferred.add(evaluated -> {
                        mcpTasks.configureIntermediateRuns(evaluated, ext.getCaches(), ext.getMinecraft(),
                                ext.getLoader(), vanillaTasks, intermediates);
                        mcpTasks.configureInitialPatches(evaluated, ext.getCaches(), ext.getPatches(), vanillaTasks, mappings);
                        distributionTasks.registerPublications(evaluated, coordinates);
                    });
                }
                case USERDEV -> {
                    if (!ext.getRegisteredUserdev().isPresent()) {
                        throw new IllegalStateException("Userdev is registered through dependencies { implementation cleanroom.userdev('version') }. "
                                + "The old cleanroom.mode, cleanroom.userdev.version and cleanroomUserdev contracts were removed.");
                    }
                    new UserDevTasks(project, ext.getCaches(), ext.getMinecraft(), ext.getRegisteredUserdev().get(), vanillaTasks);
                }
                case VANILLA -> { }
            }
            deferred.add(evaluated -> maintenanceTasks.configure(mode, evaluated));
        });

        project.afterEvaluate(evaluatedProject -> {
            for (var action : deferred) {
                action.execute(evaluatedProject);
            }
            deobfExt.wireTransformOrdering(evaluatedProject);
        });
    }

    public static final class RemovedUserdevDependency {
        public Object call(Object ignored) {
            throw new InvalidUserDataException("The cleanroomUserdev configuration was removed. Declare "
                    + "implementation cleanroom.userdev('version') instead.");
        }
    }

}
