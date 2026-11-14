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
import com.cleanroommc.gradle.api.util.Execs;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.InputArtifactDependencies;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecOperations;

import java.io.File;
import java.util.ArrayList;
import javax.inject.Inject;

/**
 * Renames a published SRG-named mod jar into this project's MCP names.
 */
@CacheableTransform
public abstract class DeobfuscateJar implements TransformAction<DeobfuscateJar.Parameters> {

    @InputArtifact
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    /**
     * The mod's own resolved graph.
     * The renamer needs it to see the type hierarchy a mod inherits from its other dependencies.
     */
    @CompileClasspath
    @InputArtifactDependencies
    public abstract FileCollection getArtifactDependencies();

    @Override
    public void transform(TransformOutputs outputs) {
        var parameters = getParameters();
        var declared = parameters.getMappings().getFiles();
        var mappings = new ArrayList<>(declared.stream().filter(File::isFile).toList());
        var libraries = new ArrayList<>(parameters.getSrgLibraries().getFiles());
        for (var directory : parameters.getUserdevInputs()) {
            mappings.add(new File(directory, UserdevConfig.SRG2MCP));
        }
        if (declared.isEmpty() && mappings.isEmpty()) {
            throw new InvalidUserDataException(
                "deobf() needs MCP mappings, which this project does not build. " +
                    "Declare a Cleanroom userdev dependency, or set cleanroom.mode to 'loader'."
            );
        }
        if (mappings.stream().noneMatch(File::isFile)) {
            throw new InvalidUserDataException(
                "The MCP mappings have not been written yet: " + declared + ". " +
                    "Run './gradlew prepareDeobf' before importing or refreshing the project in an IDE."
            );
        }

        var input = getInputArtifact().get().getAsFile();
        var name = input.getName();
        var baseName = name.endsWith(".jar") ? name.substring(0, name.length() - 4) : name;
        var output = outputs.file(baseName + "-deobf.jar");

        getArtifactDependencies().forEach(libraries::add);
        Execs.rename(getExecOperations(), parameters.getRenamerClasspath(), input, mappings, output, libraries);
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    public interface Parameters extends TransformParameters {

        @InputFiles
        @PathSensitive(PathSensitivity.NONE)
        ConfigurableFileCollection getMappings();

        @InputFiles
        @CompileClasspath
        ConfigurableFileCollection getSrgLibraries();

        @InputFiles
        @PathSensitive(PathSensitivity.RELATIVE)
        ConfigurableFileCollection getUserdevInputs();

        @Classpath
        ConfigurableFileCollection getRenamerClasspath();

        /** JVM identity rather than its executable path, so cached entries stay relocatable. */
        @Input
        Property<Integer> getJavaLanguageVersion();

        @Input
        Property<String> getJavaVendor();

    }

}
