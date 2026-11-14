/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

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
        try {
            if (!IO.extractEntry(artifact, getEntryPath().get(), output)) {
                throw new IllegalStateException(
                    artifact + " is missing required userdev entry " + getEntryPath().get() + "."
                );
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract " + getEntryPath().get() + " from " + artifact, e);
        }
    }

}
