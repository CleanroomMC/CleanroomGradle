package com.cleanroommc.gradle.api.patch.bin;

import com.cleanroommc.gradle.api.named.task.type.MavenJarExec;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

public abstract class ApplyBinPatches extends MavenJarExec {

    @InputFile
    public abstract RegularFileProperty getCleanJar();

    @InputFile
    public abstract RegularFileProperty getPatchLMZA();

    @OutputFile
    public abstract RegularFileProperty getPatchedJar();

    public ApplyBinPatches() {
        super("binarypatcher", "net.minecraftforge:binarypatcher:1.2.0");
        getMainClass().set("net.minecraftforge.binarypatcher.ConsoleTool");
        args("--clean", getCleanJar(), "--output", getPatchedJar(), "--apply", getPatchLMZA());
    }

}
