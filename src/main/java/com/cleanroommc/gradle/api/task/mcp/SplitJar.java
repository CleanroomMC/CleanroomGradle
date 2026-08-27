package com.cleanroommc.gradle.api.task.mcp;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.util.IO;
import net.minecraftforge.srgutils.IMappingFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

@CacheableTask
public abstract class SplitJar extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSourceJar();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
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
                        String name = entry.getName();
                        int innerClass = name.indexOf('$');
                        boolean minecraftClass = name.endsWith(".class") && (name.startsWith(Meta.MINECRAFT_PACKAGE_PATH)
                                || classes.contains(name)
                                || innerClass >= 0 && classes.contains(name.substring(0, innerClass) + ".class"));
                        var zos = minecraftClass ? slimZos : extraZos;
                        var newEntry = new ZipEntry(name);
                        newEntry.setTime(0L); // Fixed timestamp to keep stability
                        zos.putNextEntry(newEntry);
                        sourceZis.transferTo(zos);
                    }
                }
            }
        }
    }

}
