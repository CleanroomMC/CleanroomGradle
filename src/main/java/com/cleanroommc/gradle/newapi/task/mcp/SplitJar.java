package com.cleanroommc.gradle.newapi.task.mcp;

import com.cleanroommc.gradle.newapi.task.IntermediateProcessor;
import com.cleanroommc.gradle.newapi.util.IO;
import net.minecraftforge.srgutils.IMappingFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public abstract class SplitJar extends DefaultTask implements IntermediateProcessor {

    @InputFile
    public abstract RegularFileProperty getSourceJar();

    @InputFile
    public abstract RegularFileProperty getSrgMappingFile();

    @OutputFile
    public abstract RegularFileProperty getSlimJar();

    @OutputFile
    public abstract RegularFileProperty getExtraJar();

    @TaskAction
    public void splitJar() throws IOException {
        var classes = IMappingFile.load(this.getSrgMappingFile().get().getAsFile()).getClasses().stream()
                .map(clazz -> clazz.getOriginal() + ".class")
                .collect(Collectors.toSet());

        try (var slimZos = IO.zipOut(this.getSlimJar().get().getAsFile())) {
            try (var extraZos = IO.zipOut(this.getExtraJar().get().getAsFile())) {
                try (var sourceZis = IO.zipIn(this.getSourceJar().get().getAsFile())) {
                    for (var entry = sourceZis.getNextEntry(); entry != null; entry = sourceZis.getNextEntry()) {
                        var zos = classes.contains(entry.getName()) ? slimZos : extraZos;
                        var newEntry = new ZipEntry(entry.getName());
                        zos.putNextEntry(newEntry);
                        sourceZis.transferTo(zos);
                    }
                }
            }
        }
    }

}
