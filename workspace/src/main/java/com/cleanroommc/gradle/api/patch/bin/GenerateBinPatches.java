package com.cleanroommc.gradle.api.patch.bin;

import com.cleanroommc.gradle.api.named.task.type.MavenJarExec;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

// TODO JarProvider
public abstract class GenerateBinPatches extends MavenJarExec {

    @InputFile
    public abstract RegularFileProperty getCleanJar();

    @InputFile
    public abstract RegularFileProperty getDirtyJar();

    @InputFile
    public abstract RegularFileProperty getSrgMappingFile();

    // @Input
    // public abstract Property<String> getSide();

    @OutputFile
    public abstract RegularFileProperty getOutputLZMA();

    public GenerateBinPatches() {
        super("binarypatcher", "net.minecraftforge:binarypatcher:1.2.0");
        getMainClass().set("net.minecraftforge.binarypatcher.ConsoleTool");
        args("--clean", getCleanJar(), "--create", getDirtyJar(), "--output", getOutputLZMA(), "--srg", getSrgMappingFile());
    }

}
