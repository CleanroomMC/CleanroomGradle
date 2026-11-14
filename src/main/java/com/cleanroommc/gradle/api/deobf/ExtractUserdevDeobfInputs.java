/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.deobf;

import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.util.IO;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Exposes the mapping and SRG hierarchy packaged in a userdev artifact as inputs to the mod transform.
 */
@CacheableTransform
public abstract class ExtractUserdevDeobfInputs implements TransformAction<TransformParameters.None> {

    @InputArtifact
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        var input = getInputArtifact().get().getAsFile();
        var layout = UserdevConfig.readFromJar(input).layout();
        var output = outputs.dir("userdev-deobf-inputs");
        try {
            if (!IO.extractEntry(input, layout.srgToMcp(), output.toPath().resolve(UserdevConfig.SRG2MCP))) {
                throw new InvalidUserDataException(
                    input + " does not contain " + layout.srgToMcp() +
                        ". Use a userdev artifact produced by a CleanroomGradle version that supports native deobf()."
                );
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not extract deobf inputs from " + input, e);
        }
    }

}
