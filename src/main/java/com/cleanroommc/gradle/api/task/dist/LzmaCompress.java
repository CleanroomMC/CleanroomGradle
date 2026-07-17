package com.cleanroommc.gradle.api.task.dist;

import com.cleanroommc.gradle.api.util.IO;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.LZMAOutputStream;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * LZMA compresses a single file into the raw {@code .lzma} container
 * using the pure-Java {@code org.tukaani:xz} implementation.
 */
public abstract class LzmaCompress extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @TaskAction
    public void compress() {
        var input = this.getInput().get().getAsFile();
        var output = this.getOutput().get().getAsFile();
        try {
            FileUtils.createParentDirectories(output);
            try (var in = IO.bufferedIn(input);
                 var out = IO.out(output);
                 var lzma = new LZMAOutputStream(out, new LZMA2Options(), true)) {
                in.transferTo(lzma);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to LZMA compress " + input, e);
        }
    }

}
