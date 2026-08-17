package com.cleanroommc.gradle.api.task;

import com.cleanroommc.gradle.api.ext.ProjectMode;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;

@DisableCachingByDefault(because = "Prints build diagnostics")
public abstract class CleanroomInfo extends DefaultTask {

    @Input
    public abstract Property<String> getPluginVersion();

    @Input
    public abstract Property<ProjectMode> getMode();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract Property<Boolean> getOffline();

    @Input
    public abstract Property<Boolean> getDiscardIntermediates();

    @Input
    public abstract Property<String> getNamesSource();

    @Input
    public abstract MapProperty<String, String> getTools();

    @Internal
    public abstract DirectoryProperty getSharedCacheDirectory();

    @Internal
    public abstract DirectoryProperty getVersionCacheDirectory();

    @Internal
    public abstract DirectoryProperty getLocalCacheDirectory();

    @Internal
    public abstract MapProperty<String, String> getOfflineCacheFiles();

    @TaskAction
    public void printInfo() {
        var logger = getLogger();
        logger.lifecycle("CleanroomGradle {}", getPluginVersion().get());
        logger.lifecycle("  mode: {}", getMode().get().name().toLowerCase());
        logger.lifecycle("  Minecraft: {}", getMinecraftVersion().get());
        logger.lifecycle("  offline: {}", getOffline().get());
        logger.lifecycle("  discard intermediates: {}", getDiscardIntermediates().get());
        logger.lifecycle("  names: {}", getNamesSource().get());
        logger.lifecycle("  shared cache: {}", getSharedCacheDirectory().get().getAsFile());
        logger.lifecycle("  version cache: {}", getVersionCacheDirectory().get().getAsFile());
        logger.lifecycle("  local cache: {}", getLocalCacheDirectory().get().getAsFile());
        logger.lifecycle("  tools:");
        getTools().get().forEach((name, notation) -> logger.lifecycle("    {}: {}", name, notation));
        logger.lifecycle("  offline client readiness:");
        getOfflineCacheFiles().get().forEach((name, path) -> logger.lifecycle("    {}: {} ({})", name, new File(path).isFile() ? "ready" : "missing", path));
    }

}
