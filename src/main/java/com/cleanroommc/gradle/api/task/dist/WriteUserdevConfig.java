package com.cleanroommc.gradle.api.task.dist;

import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.google.gson.GsonBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Writes the {@code config.json} that {@code userdevJar} ships. */
@CacheableTask
public abstract class WriteUserdevConfig extends DefaultTask {

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract Property<String> getCleanroomVersion();

    @Input
    public abstract Property<String> getForgeVersion();

    @Input
    public abstract Property<String> getMcpConfig();

    @Input
    public abstract Property<String> getBinpatches();

    @Input
    public abstract Property<String> getClientBinpatches();

    @Input
    public abstract Property<String> getServerBinpatches();

    @Input
    public abstract Property<String> getSrg2Mcp();

    @Input
    public abstract Property<String> getMcp2Srg();

    @Input
    public abstract ListProperty<String> getAccessTransformers();

    @Input
    public abstract ListProperty<String> getLibraries();

    @Input
    public abstract Property<String> getLoaderGroup();

    @Input
    public abstract Property<String> getClientMainClass();

    @Input
    public abstract Property<String> getServerMainClass();

    @Input
    public abstract Property<String> getLaunchClass();

    @Input
    public abstract Property<String> getClientTweakClass();

    @Input
    public abstract Property<String> getServerTweakClass();

    @Input
    public abstract Property<String> getClientTarget();

    @Input
    public abstract Property<String> getServerTarget();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    public WriteUserdevConfig() {
        // TODO
        getClientMainClass().convention("com.cleanroommc.boot.MainClient");
        getServerMainClass().convention("com.cleanroommc.boot.MainServer");
        // TODO
        getLaunchClass().convention("top.outlands.foundation.boot.Foundation");
        getClientTweakClass().convention("net.minecraftforge.fml.common.launcher.FMLTweaker");
        getServerTweakClass().convention("net.minecraftforge.fml.common.launcher.FMLServerTweaker");
        // TODO
        getClientTarget().convention("fmldevclient");
        getServerTarget().convention("fmldevserver");
    }

    @TaskAction
    public void write() {
        var runs = new UserdevConfig.Runs(
                new UserdevConfig.Run(getClientMainClass().get(), getLaunchClass().get(), getClientTweakClass().get(), getClientTarget().get()),
                new UserdevConfig.Run(getServerMainClass().get(), getLaunchClass().get(), getServerTweakClass().get(), getServerTarget().get()));
        var config = new UserdevConfig(
                UserdevConfig.SPEC,
                getMinecraftVersion().get(),
                getCleanroomVersion().get(),
                getForgeVersion().get(),
                getMcpConfig().get(),
                getBinpatches().get(),
                getClientBinpatches().get(),
                getServerBinpatches().get(),
                getSrg2Mcp().get(),
                getMcp2Srg().get(),
                getAccessTransformers().get(),
                getLibraries().get(),
                getLoaderGroup().get(),
                runs);
        var output = getOutput().getAsFile().get().toPath();
        try {
            var parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            var json = new GsonBuilder().setPrettyPrinting().create().toJson(config);
            Files.writeString(output, json + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + output, e);
        }
    }

}
