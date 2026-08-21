package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.ext.ProjectMode;
import com.cleanroommc.gradle.api.task.CleanroomInfo;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MaintenanceTasks {

    private static final String GROUP_NAME = "cleanroom maintenance";

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

    public final TaskProvider<Delete> cleanCleanroomSharedCache;
    public final TaskProvider<CleanroomInfo> cleanroomInfo;

    public MaintenanceTasks(Project project, CleanroomExtension ext, VanillaTasks vanillaTasks, String pluginVersion) {
        this.cleanCleanroomSharedCache = project.getTasks().register("cleanCleanroomSharedCache", Delete.class, task -> {
            task.setGroup(GROUP_NAME);
            task.setDescription("Deletes the configured shared CleanroomGradle cache.");
            task.delete(ext.getCacheDirectory());
        });
        this.cleanroomInfo = project.getTasks().register("cleanroomInfo", CleanroomInfo.class, task -> {
            task.setGroup(GROUP_NAME);
            task.setDescription("Prints the effective CleanroomGradle mode, versions, caches, tools, and offline readiness.");
            task.getPluginVersion().set(pluginVersion);
            task.getMinecraftVersion().set(vanillaTasks.minecraftVersion);
            task.getOffline().set(project.getGradle().getStartParameter().isOffline());
            task.getDiscardIntermediates().set(ext.getDiscardIntermediates());
            task.getNamesSource().set(ext.getNamesDirectory()
                    .map(directory -> "Tiny v2 (" + directory.file("mappings.tiny").getAsFile() + ")")
                    .orElse("MCP CSV dependency"));
            task.getSharedCacheDirectory().set(ext.getCacheDirectory());
            task.getVersionCacheDirectory().set(vanillaTasks.versionCacheDirectory);
            task.getLocalCacheDirectory().set(ext.getLocalCacheDirectory());
            task.getOfflineCacheFiles().put("client jar", vanillaTasks.versionCacheDirectory
                    .map(directory -> directory.file("client.jar").getAsFile().getAbsolutePath()));
            task.getOfflineCacheFiles().put("server jar", vanillaTasks.versionCacheDirectory
                    .map(directory -> directory.file("server.jar").getAsFile().getAbsolutePath()));
            task.getOfflineCacheFiles().put("asset index", ext.getCacheDirectory().file(
                            vanillaTasks.versionMeta.map(meta -> "assets/indexes/" + meta.assetIndexId() + ".json"))
                    .map(file -> file.getAsFile().getAbsolutePath()));
        });
    }

    public void configure(ProjectMode mode, Project project) {
        this.cleanroomInfo.configure(task -> {
            task.getMode().set(mode);
            task.getTools().set(configuredTools(project));
        });
    }

}
