package com.cleanroommc.gradle.newapi.task.mcp;

import com.cleanroommc.gradle.newapi.task.IntermediateProcessor;
import com.cleanroommc.gradle.newapi.task.MavenJarExec;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

public abstract class MergeJars extends MavenJarExec implements IntermediateProcessor {

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
        super("mergetool", "net.minecraftforge:mergetool:1.2.2");
        this.getMainClass().set("net.minecraftforge.mergetool.ConsoleMerger");
        this.args("--client", this.getClientJar(),
                "--server", this.getServerJar(),
                "--output", this.getMergedJar(),
                "--whitelist-map", this.getSrgMappingFile(),
                "-ann", this.getMinecraftVersion());
    }

}
