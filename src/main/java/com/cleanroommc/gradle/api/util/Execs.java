/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.process.ExecOperations;
import org.gradle.process.JavaExecSpec;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public final class Execs {

    private Execs() { }

    /**
     * Runs a tool with its console output buffered and replayed only when it fails.
     * The merge, access transformer and renamer tools each print every entry they process,
     * which buries a fresh workspace's first build under thousands of lines.
     *
     * @param operations runs the tool
     * @param action configures the tool's launch, after the output streams are redirected
     */
    public static void quietJavaExec(ExecOperations operations, Action<? super JavaExecSpec> action) {
        var output = new ByteArrayOutputStream();
        var result = operations.javaexec(spec -> {
            spec.setStandardOutput(output);
            spec.setErrorOutput(output);
            spec.setIgnoreExitValue(true);
            action.execute(spec);
        });
        if (result.getExitValue() != 0) {
            throw new GradleException(
                "Tool exited with code " + result.getExitValue() + ':' + System.lineSeparator() +
                    output.toString(StandardCharsets.UTF_8)
            );
        }
    }

    /**
     * Runs the renamer over {@code input}, applying each of {@code mappings} in turn.
     *
     * @param operations runs the renamer
     * @param classpath the renamer's fat jar
     * @param input the jar to rename
     * @param mappings the mapping files, in the order they are applied
     * @param output where the renamed jar is written
     * @param libraries the jars the renamer resolves inherited names against
     * @param flags extra renamer options
     */
    public static void rename(
        ExecOperations operations, FileCollection classpath, File input, List<File> mappings, File output,
        Iterable<File> libraries, String... flags) {
        var arguments = new ArrayList<>(List.of("--input", input.getAbsolutePath()));
        for (var mapping : mappings) {
            arguments.add("--map");
            arguments.add(mapping.getAbsolutePath());
        }
        arguments.add("--output");
        arguments.add(output.getAbsolutePath());
        arguments.addAll(List.of(flags));
        for (var library : libraries) {
            arguments.add("--lib");
            arguments.add(library.getAbsolutePath());
        }
        quietJavaExec(operations, spec -> {
            spec.setClasspath(classpath);
            spec.getMainClass().set(mainClassOf(classpath));
            spec.setArgs(arguments);
        });
    }

    /**
     * The renamer ships as an executable fat jar, so its entry point comes from the manifest rather than
     * from any property the renamer plugin exposes.
     */
    private static String mainClassOf(Iterable<File> classpath) {
        for (var file : classpath) {
            try (var jar = new JarFile(file)) {
                var manifest = jar.getManifest();
                if (manifest != null) {
                    var mainClass = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
                    if (mainClass != null) {
                        return mainClass;
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + file, e);
            }
        }
        throw new GradleException("No Main-Class found on the renamer classpath: " + classpath);
    }

}
