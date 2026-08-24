package com.cleanroommc.gradle.env;

import com.cleanroommc.gradle.api.ext.CachesExtension;
import com.cleanroommc.gradle.api.ext.MappingsExtension;
import com.cleanroommc.gradle.api.ext.ProjectMode;
import com.cleanroommc.gradle.api.task.CleanroomInfo;
import com.cleanroommc.gradle.api.task.Tasks;
import org.gradle.api.Project;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;

public class MaintenanceTasks {

    private static final String GROUP_NAME = "cleanroom maintenance";

    public final TaskProvider<Delete> cleanCleanroomSharedCache;
    public final TaskProvider<CleanroomInfo> cleanroomInfo;

    public MaintenanceTasks(Project project, CachesExtension caches, MappingsExtension mappings, VanillaTasks vanillaTasks, String pluginVersion) {
        this.cleanCleanroomSharedCache = Tasks.register(project, "cleanCleanroomSharedCache", Delete.class);
        this.cleanroomInfo = project.getTasks().register("cleanroomInfo", CleanroomInfo.class, task -> {
            task.setGroup(GROUP_NAME);
            task.setDescription("Prints the effective CleanroomGradle mode, versions, caches, tools, and offline readiness.");
            task.getPluginVersion().set(pluginVersion);
            task.getMinecraftVersion().set(vanillaTasks.minecraftVersion);
            task.getOffline().set(project.getGradle().getStartParameter().isOffline());
            task.getDiscardIntermediates().set(caches.getDiscardIntermediates());
            task.getNamesSource().set(mappings.getNamesDirectory()
                    .map(directory -> "Tiny v2 (" + directory.file(MappingsExtension.NAMES_FILE).getAsFile() + ")")
                    .orElse("MCP CSV dependency"));
            task.getSharedCacheDirectory().set(caches.getDirectory());
            task.getVersionCacheDirectory().set(vanillaTasks.versionCacheDirectory);
            task.getLocalCacheDirectory().set(caches.getLocalDirectory());
            task.getOfflineCacheFiles().put("client jar", vanillaTasks.clientJar.map(File::getAbsolutePath));
            task.getOfflineCacheFiles().put("server jar", vanillaTasks.serverJar.map(File::getAbsolutePath));
            task.getOfflineCacheFiles().put("asset index", vanillaTasks.assetIndexFile.map(File::getAbsolutePath));
        });
        this.cleanCleanroomSharedCache.configure(task -> {
            task.setGroup(GROUP_NAME);
            task.setDescription("Deletes the configured shared CleanroomGradle cache.");
            task.delete(caches.getDirectory());
        });
    }

    public void configure(ProjectMode mode, Project project) {
        this.cleanroomInfo.configure(task -> {
            task.getMode().set(mode);
            task.getTools().set(ToolConfigs.configured(project));
        });
    }

}
