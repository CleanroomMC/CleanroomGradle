package com.cleanroommc.gradle.api.task.userdev;

import com.cleanroommc.gradle.api.util.IO;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.zip.ZipFile;

@CacheableTask
public abstract class ExtractUserdevFile extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getUserdevArtifact();

    @Input
    public abstract Property<String> getEntryPath();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @TaskAction
    public void extract() {
        var artifact = getUserdevArtifact().getAsFile().get();
        var output = getOutput().getAsFile().get().toPath();
        try (var zip = new ZipFile(artifact)) {
            var entry = zip.getEntry(getEntryPath().get());
            if (entry == null || entry.isDirectory()) {
                throw new IllegalStateException(artifact + " is missing required userdev entry " + getEntryPath().get() + ".");
            }
            Files.createDirectories(output.getParent());
            try (var input = zip.getInputStream(entry)) {
                Files.copy(input, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract " + getEntryPath().get() + " from " + artifact, e);
        }
    }

}
