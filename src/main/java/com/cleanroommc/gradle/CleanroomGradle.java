package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.task.CleanroomInfo;
import com.cleanroommc.gradle.api.task.IntermediateProcessor;
import com.cleanroommc.gradle.api.util.CleanroomProblems;
import com.cleanroommc.gradle.api.util.CloseHttpClientFlowAction;
import com.cleanroommc.gradle.api.util.LwjglNatives;
import com.cleanroommc.gradle.api.util.Objects;
import com.cleanroommc.gradle.env.CleanroomTasks;
import com.cleanroommc.gradle.env.DistributionTasks;
import com.cleanroommc.gradle.env.MCPTasks;
import com.cleanroommc.gradle.env.UserDevTasks;
import com.cleanroommc.gradle.env.VanillaTasks;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.problems.Problems;
import org.gradle.api.tasks.Delete;

import javax.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

        final var cleanroomExtension = Objects.extension(project, "cleanroom", CleanroomExtension.class);
        IntermediateProcessor.register(project, cleanroomExtension);
        project.getPluginManager().withPlugin("base", plugin -> project.getTasks().named("clean", Delete.class)
                .configure(task -> task.delete(cleanroomExtension.getLocalCacheDirectory())));
        project.getTasks().register("cleanCleanroomSharedCache", Delete.class, task -> {
            task.setGroup("cleanroom maintenance");
            task.setDescription("Deletes the configured shared CleanroomGradle cache.");
            task.delete(cleanroomExtension.getCacheDirectory());
        });
        LwjglNatives.register(project, cleanroomExtension);

        final var vanillaTasks = new VanillaTasks(project, cleanroomExtension);
        final var mcpTasks = new MCPTasks(project, cleanroomExtension, vanillaTasks);
        UserDevTasks.configuration(project);

        var info = project.getTasks().register("cleanroomInfo", CleanroomInfo.class, task -> {
            task.setGroup("help");
            task.setDescription("Prints the effective CleanroomGradle mode, versions, caches, tools, and offline readiness.");
            task.getPluginVersion().set(pluginVersion);
            task.getMinecraftVersion().set(vanillaTasks.minecraftVersion);
            task.getOffline().set(project.getGradle().getStartParameter().isOffline());
            task.getDiscardIntermediates().set(cleanroomExtension.getDiscardIntermediates());
            task.getNamesSource().set(cleanroomExtension.getNamesDirectory()
                    .map(directory -> "Tiny v2 (" + directory.file("mappings.tiny").getAsFile() + ")")
                    .orElse("MCP CSV dependency"));
            task.getSharedCacheDirectory().set(cleanroomExtension.getCacheDirectory());
            task.getVersionCacheDirectory().set(vanillaTasks.versionCacheDirectory);
            task.getLocalCacheDirectory().set(cleanroomExtension.getLocalCacheDirectory());
            task.getOfflineCacheFiles().put("client jar", vanillaTasks.versionCacheDirectory
                    .map(directory -> directory.file("client.jar").getAsFile().getAbsolutePath()));
            task.getOfflineCacheFiles().put("server jar", vanillaTasks.versionCacheDirectory
                    .map(directory -> directory.file("server.jar").getAsFile().getAbsolutePath()));
            task.getOfflineCacheFiles().put("asset index", cleanroomExtension.getCacheDirectory().file(
                    vanillaTasks.versionMeta.map(meta -> "assets/indexes/" + meta.assetIndexId() + ".json"))
                    .map(file -> file.getAsFile().getAbsolutePath()));
        });
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
            info.configure(task -> {
                task.getMode().set(mode);
                task.getTools().set(configuredTools(evaluatedProject));
            });
        });
    }

    private static LinkedHashMap<String, String> configuredTools(Project project) {
        var tools = new LinkedHashMap<String, String>();
        Meta.DEFAULT_TOOLS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var configuration = project.getConfigurations().findByName(entry.getKey());
            var declared = configuration == null ? Set.<Dependency>of() : configuration.getDependencies();
            tools.put(entry.getKey(), declared.isEmpty() ? entry.getValue() : declared.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", ")));
        });
        return tools;
    }

}
