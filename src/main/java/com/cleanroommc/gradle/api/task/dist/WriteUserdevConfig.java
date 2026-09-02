package com.cleanroommc.gradle.api.task.dist;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.util.IO;
import com.google.gson.GsonBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Writes the {@code config.json} that {@code userdevJar} ships.
 */
@CacheableTask
public abstract class WriteUserdevConfig extends DefaultTask {

    @Input
    public abstract Property<String> getCleanroomVersion();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract Property<String> getForgeVersion();

    @Input
    public abstract Property<String> getMcpConfig();

    @Input
    public abstract Property<String> getMcpMappings();

    @Input
    public abstract Property<String> getInitialPatches();

    @Input
    public abstract MapProperty<String, String> getTools();

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
    public abstract Property<String> getSideAnnotationStrippers();

    @Input
    public abstract Property<String> getPatches();

    @Input
    public abstract Property<String> getLoaderGroup();

    @Input
    public abstract Property<String> getClientUrl();

    @Input
    public abstract Property<String> getClientSha1();

    @Input
    public abstract Property<String> getServerUrl();

    @Input
    public abstract Property<String> getServerSha1();

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
        getMinecraftVersion().convention(Meta.ONE_TRUE_MINECRAFT_VERSION);
    }

    @TaskAction
    public void write() {
        var runs = new UserdevConfig.Runs(
                new UserdevConfig.Run(getClientMainClass().get(), getLaunchClass().get(), getClientTweakClass().get(), getClientTarget().get()),
                new UserdevConfig.Run(getServerMainClass().get(), getLaunchClass().get(), getServerTweakClass().get(), getServerTarget().get()));
        var config = new UserdevConfig(UserdevConfig.SPEC,
                new UserdevConfig.Minecraft(getMinecraftVersion().get(),
                        new UserdevConfig.Download(getClientUrl().get(), getClientSha1().get()),
                        new UserdevConfig.Download(getServerUrl().get(), getServerSha1().get())),
                new UserdevConfig.Loader(getCleanroomVersion().get(), getForgeVersion().get(), getLoaderGroup().get()),
                new UserdevConfig.Inputs(getMcpConfig().get(), getMcpMappings().get(), getInitialPatches().get(), getTools().get()),
                new UserdevConfig.Layout(getBinpatches().get(), getClientBinpatches().get(), getServerBinpatches().get(),
                        UserdevConfig.meta(UserdevConfig.OBF2SRG), getSrg2Mcp().get(), getMcp2Srg().get(),
                        UserdevConfig.meta(UserdevConfig.ACCESS), UserdevConfig.meta(UserdevConfig.CONSTRUCTORS),
                        UserdevConfig.meta(UserdevConfig.EXCEPTIONS), UserdevConfig.meta(UserdevConfig.METHODS),
                        UserdevConfig.meta(UserdevConfig.FIELDS), UserdevConfig.meta(UserdevConfig.PARAMS),
                        UserdevConfig.meta(UserdevConfig.DEOBF_LIBRARY), UserdevConfig.meta(UserdevConfig.SOURCE_INPUT),
                        UserdevConfig.meta("client-extra"), UserdevConfig.meta("server-extra"),
                        UserdevConfig.meta(UserdevConfig.INITIAL_PATCHES), getAccessTransformers().get(),
                        getSideAnnotationStrippers().get(), getPatches().get(), UserdevConfig.meta(UserdevConfig.LOADER_SOURCES)),
                runs);
        var output = getOutput().getAsFile().get().toPath();
        try {
            var json = new GsonBuilder().setPrettyPrinting().create().toJson(config);
            IO.writeString(output, json + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + output, e);
        }
    }

}
