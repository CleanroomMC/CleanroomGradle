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

import com.cleanroommc.gradle.api.schema.UserdevConfig;

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
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.ExecOperations;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

@CacheableTransform
public abstract class MaterializeUserdevClasses implements TransformAction<MaterializeUserdevClasses.Parameters> {

    /** Cleanroom skips this name when it scans the classpath for mods. */
    public static final String MINECRAFT = "minecraft-patched.jar";
    public static final String LOADER = "cleanroom-userdev.jar";

    @InputArtifact
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        var prepared = getInputArtifact().get().getAsFile();
        var userdev = new File(prepared, PrepareUserdev.USERDEV);
        var config = UserdevConfig.readFromJar(userdev);
        var minecraft = outputs.file(MINECRAFT);
        var loader = outputs.file(LOADER);
        var work = minecraft.toPath().resolveSibling("classes-work");
        try {
            UserdevArchive.delete(work);
            Files.createDirectories(work);
            var minecraftSrg = new File(prepared, PrepareUserdev.MINECRAFT_SRG);
            var loaderSrg = work.resolve("loader-srg.jar").toFile();
            UserdevArchive.select(
                userdev, loaderSrg,
                name -> !name.startsWith(UserdevConfig.META + "/") && !name.matches("META-INF/[^/]+\\.(SF|RSA|DSA)"),
                ""
            );
            var mappings = UserdevArchive.extract(
                userdev, config.layout().srgToMcp(), work.resolve("srg2mcp.tsrg").toFile()
            );

            var ats = UserdevArchive.accessTransformers(userdev, config.layout().accessTransformers(), work);
            ats.addAll(getParameters().getAccessTransformers().getFiles());
            var minecraftAt = work.resolve("minecraft-at.jar").toFile();
            UserdevArchive.accessTransform(
                getExecOperations(), getParameters().getAccessTransformerClasspath(), minecraftSrg, minecraftAt, ats,
                work.toFile()
            );

            rename(minecraftAt, minecraft, mappings, loaderSrg);
            rename(loaderSrg, loader, mappings, minecraftSrg);
            UserdevArchive.delete(work);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to materialize classes from " + userdev, e);
        }
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    private void rename(File input, File output, File mappings, File library) {
        var libraries = new ArrayList<>(List.of(library));
        getParameters().getLibraries().forEach(libraries::add);
        UserdevArchive.rename(
            getExecOperations(), getParameters().getRenamerClasspath(), input, output, mappings, libraries
        );
    }

    public interface Parameters extends TransformParameters {

        @InputFiles
        @PathSensitive(PathSensitivity.NONE)
        ConfigurableFileCollection getAccessTransformers();

        // A chained transform would be handed the libraries run through PrepareUserdev, so they come in here
        @CompileClasspath
        ConfigurableFileCollection getLibraries();

        @Classpath
        ConfigurableFileCollection getRenamerClasspath();

        @Classpath
        ConfigurableFileCollection getAccessTransformerClasspath();

    }

}
