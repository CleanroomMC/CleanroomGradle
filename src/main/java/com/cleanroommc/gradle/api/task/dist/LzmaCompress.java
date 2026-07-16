package com.cleanroommc.gradle.api.task.dist;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.LZMAOutputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/**
 * LZMA-compresses a single file into the raw {@code .lzma} container using the pure-Java
 * {@code org.tukaani:xz} implementation. Mirrors the old buildscript's {@code deobfDataLzma}.
 */
public abstract class LzmaCompress extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @TaskAction
    public void compress() {
        var input = this.getInput().get().getAsFile().toPath();
        var output = this.getOutput().get().getAsFile().toPath();
        try {
            var parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (var in = new BufferedInputStream(Files.newInputStream(input));
                 OutputStream out = Files.newOutputStream(output);
                 var lzma = new LZMAOutputStream(out, new LZMA2Options(), true)) {
                in.transferTo(lzma);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to LZMA-compress " + input, e);
        }
    }

}
