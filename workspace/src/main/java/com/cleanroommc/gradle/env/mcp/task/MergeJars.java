package com.cleanroommc.gradle.env.mcp.task;

import com.cleanroommc.gradle.api.named.task.JarTransformer;
import com.cleanroommc.gradle.api.named.task.type.MavenJarExec;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

public abstract class MergeJars extends MavenJarExec implements JarTransformer {

    @InputFile
    public abstract RegularFileProperty getClientJar();

    @InputFile
    public abstract RegularFileProperty getServerJar();

    @InputFile
    public abstract RegularFileProperty getSrgMappingFile();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @OutputFile
    public abstract RegularFileProperty getMergedJar();

    public MergeJars() {
        // super("mergetool", "net.minecraftforge:mergetool:1.2.0");
        // super("mergetool", "net.minecraftforge:mergetool:cleanroom-1.0");
        super("mergetool", "net.minecraftforge:mergetool:1.2.2");
        getMainClass().set("net.minecraftforge.mergetool.ConsoleMerger");
        args("--client", getClientJar(),
             "--server", getServerJar(),
             "--output", getMergedJar(),
             "--whitelist-map", getSrgMappingFile(),
             "-ann", getMinecraftVersion());
        setup(false);
    }

}
