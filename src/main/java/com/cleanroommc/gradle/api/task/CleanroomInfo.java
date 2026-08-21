package com.cleanroommc.gradle.api.task;

import com.cleanroommc.gradle.api.ext.ProjectMode;
import com.cleanroommc.gradle.api.util.EnumValues;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;

@DisableCachingByDefault(because = "Prints build diagnostics")
public abstract class CleanroomInfo extends DefaultTask {

    private final Property<ProjectMode> mode;

    @Input
    public abstract Property<String> getPluginVersion();

    @Input
    public Property<ProjectMode> getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode.set(EnumValues.parse(ProjectMode.class, mode));
    }

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

    @Inject
    public CleanroomInfo(ObjectFactory objects) {
        this.mode = objects.property(ProjectMode.class);
    }

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
