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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;

@CacheableTransform
public abstract class MaterializeUserdevSources implements TransformAction<MaterializeUserdevSources.Parameters> {

    public interface Parameters extends TransformParameters {

        @Classpath
        ConfigurableFileCollection getDecompilerClasspath();

        @CompileClasspath
        ConfigurableFileCollection getLibraries();

    }

    @InputArtifact
    @PathSensitive(PathSensitivity.NONE)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Override
    public void transform(TransformOutputs outputs) {
        var input = getInputArtifact().get().getAsFile();
        var output = outputs.file("cleanroom-userdev-materialized-sources.jar");
        var work = output.toPath().resolveSibling("sources-work");
        try {
            UserdevSourceMaterializer.materialize(
                    input,
                    output.toPath(),
                    work,
                    getParameters().getLibraries(),
                    getParameters().getDecompilerClasspath(),
                    getExecOperations()
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to materialize sources from " + input, e);
        }
    }

}
