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

import com.cleanroommc.gradle.api.util.Execs;
import com.cleanroommc.gradle.api.util.IO;

import org.gradle.api.file.FileCollection;
import org.gradle.process.ExecOperations;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipFile;

final class UserdevArchive {

    private UserdevArchive() { }

    static void select(File input, File output, Predicate<String> include, String stripPrefix) {
        try (var zip = new ZipFile(input); var out = IO.zipOut(output)) {
            for (var entry : IO.sortedEntries(zip).entrySet()) {
                var name = entry.getKey();
                if (include.test(name) && name.length() > stripPrefix.length()) {
                    IO.writeEntry(out, name.substring(stripPrefix.length()), IO.readEntry(zip, entry.getValue()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to materialize " + input, e);
        }
    }

    static File extract(File jar, String path, File output) throws IOException {
        if (!IO.extractEntry(jar, path, output.toPath())) {
            throw new IllegalStateException("Missing required userdev entry " + path + ".");
        }
        return output;
    }

    static void accessTransform(
        ExecOperations exec, FileCollection classpath, File input, File output, List<File> ats, File workingDir
    ) throws IOException {
        if (ats.isEmpty()) {
            Files.copy(input.toPath(), output.toPath());
            return;
        }
        var arguments = new ArrayList<>(List.of(
            "--inJar", input.getAbsolutePath(), "--outJar", output.getAbsolutePath(), "--logFile",
            "accesstransform.log"
        ));
        for (var at : ats) {
            arguments.add("--atFile");
            arguments.add(at.getAbsolutePath());
        }
        Execs.quietJavaExec(exec, spec -> {
            spec.setClasspath(classpath);
            spec.getMainClass().set("net.minecraftforge.accesstransformer.TransformerProcessor");
            spec.setArgs(arguments);
            spec.setWorkingDir(workingDir);
        });
    }

    /** The access transformers the artifact ships, extracted under {@code work}. */
    static List<File> accessTransformers(File userdev, List<String> paths, Path work) throws IOException {
        var ats = new ArrayList<File>();
        for (var path : paths) {
            ats.add(extract(userdev, path, work.resolve("ats").resolve(new File(path).getName()).toFile()));
        }
        return ats;
    }

}
