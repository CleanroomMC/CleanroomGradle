/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.userdev;

import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/** Takes one jar out of the directory {@link PrepareUserdev} produced. */
@CacheableTransform
public abstract class SelectPrepared implements TransformAction<SelectPrepared.Parameters> {

    @InputArtifact
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        var name = getParameters().getFile().get();
        var source = new File(getInputArtifact().get().getAsFile(), name);
        try {
            Files.copy(source.toPath(), outputs.file(name).toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy " + source, e);
        }
    }

    public interface Parameters extends TransformParameters {

        @Input
        Property<String> getFile();

    }

}
