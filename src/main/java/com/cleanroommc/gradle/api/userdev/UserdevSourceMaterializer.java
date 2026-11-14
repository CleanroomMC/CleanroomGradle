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

import com.cleanroommc.gradle.api.names.SourceRenamer;
import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.task.patch.ApplyDiffs;
import com.cleanroommc.gradle.api.util.Execs;
import com.cleanroommc.gradle.api.util.IO;

import de.siegmar.fastcsv.reader.CsvReader;
import org.apache.commons.io.FileUtils;

import org.gradle.api.file.FileCollection;
import org.gradle.process.ExecOperations;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

/** Materializes the sources shared by the published classifier and the consumer transform. */
public final class UserdevSourceMaterializer {

    private UserdevSourceMaterializer() { }

    public static void materialize(
        File input, File srgJar, Path output, Path work, Iterable<File> dependencies,
        FileCollection decompilerClasspath, ExecOperations execOperations
    ) throws IOException {
        var config = UserdevConfig.readFromJar(input);
        FileUtils.deleteDirectory(work.toFile());
        Files.createDirectories(work);
        var decompiled = work.resolve("decompiled.jar");
        var arguments = new ArrayList<String>();
        arguments.add("--new-line-separator=1");
        arguments.add("--ascii-strings=1");
        arguments.add("--include-classpath=1");
        arguments.add("--jad-style-variable-naming=1");
        arguments.add("--thread-count=-1");
        arguments.add("--indent-string=    ");
        for (var dependency : dependencies) {
            arguments.add("-e=" + dependency.getAbsolutePath());
        }
        arguments.add(srgJar.getAbsolutePath());
        arguments.add(decompiled.toAbsolutePath().toString());
        Execs.quietJavaExec(execOperations, spec -> {
            spec.setClasspath(decompilerClasspath);
            spec.getMainClass().set("org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler");
            spec.setArgs(arguments);
        });

        var srg = work.resolve("srg");
        unzip(decompiled, srg, "");
        var initial = work.resolve("initial-patches");
        unzip(input.toPath(), initial, config.layout().initialPatches() + "/");
        applyPatches(srg, initial);

        var names = new HashMap<String, String>();
        var docs = new HashMap<String, String>();
        readMappings(input, config.layout().methods(), names, docs);
        readMappings(input, config.layout().fields(), names, docs);
        readMappings(input, config.layout().params(), names, docs);
        var mcp = work.resolve("mcp");
        remapSources(srg, mcp, names, docs);

        var cleanroomPatches = work.resolve("cleanroom-patches");
        unzip(input.toPath(), cleanroomPatches, config.layout().patches() + "/");
        applyPatches(mcp, cleanroomPatches);
        unzip(input.toPath(), mcp, config.layout().loaderSources() + "/");
        StripHiddenFromUserdev.stripSources(mcp, config.loader().hiddenAnnotation());
        zip(mcp, output);
        FileUtils.deleteDirectory(work.toFile());
    }

    static void applyPatches(Path source, Path patches) throws IOException {
        if (!Files.isDirectory(patches)) {
            return;
        }
        try (var files = Files.walk(patches)) {
            for (var patch : files.filter(file -> file.getFileName().toString().endsWith(".patch")).toList()) {
                var relative = patches.relativize(patch).toString();
                var target = source.resolve(relative.substring(0, relative.length() - ".patch".length()));
                if (!Files.isRegularFile(target)) {
                    throw new IllegalStateException(
                        "Userdev patch " + relative + " has no target: expected " + target +
                            ". The artifact's patch set and its decompiled sources are out of sync."
                    );
                }
                try {
                    IO.writeLines(target, ApplyDiffs.patch(target, patch));
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to apply userdev patch " + relative, e);
                }
            }
        }
    }

    private static void readMappings(File input, String entryName, Map<String, String> names, Map<String, String> docs)
        throws IOException {
        var csv = IO.readEntry(input, entryName);
        if (csv == null) {
            throw new IllegalStateException("Missing required userdev entry " + entryName + ".");
        }
        try (var reader = CsvReader.builder().ofNamedCsvRecord(new String(csv, StandardCharsets.UTF_8))) {
            for (var record : reader) {
                var key = record.getField(record.getHeader().contains("searge") ? "searge" : "param");
                names.put(key, record.getField("name"));
                if (record.getHeader().contains("desc")) {
                    var doc = record.getField("desc");
                    if (!doc.isEmpty()) {
                        docs.put(key, doc);
                    }
                }
            }
        }
    }

    private static void remapSources(Path source, Path destination, Map<String, String> names, Map<String, String> docs)
        throws IOException {
        try (var files = Files.walk(source)) {
            for (var file : files.filter(Files::isRegularFile).toList()) {
                var target = destination.resolve(source.relativize(file));
                Files.createDirectories(target.getParent());
                if (file.getFileName().toString().endsWith(".java")) {
                    IO.writeLines(
                        target, SourceRenamer.rename(Files.readAllLines(file, StandardCharsets.UTF_8), names, docs)
                    );
                } else {
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void unzip(Path archive, Path destination, String prefix) throws IOException {
        try (var zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(prefix)) {
                    continue;
                }
                var relative = entry.getName().substring(prefix.length());
                if (relative.isEmpty()) {
                    continue;
                }
                var output = destination.resolve(relative).normalize();
                if (!output.startsWith(destination)) {
                    throw new IllegalStateException("Unsafe archive entry " + entry.getName() + ".");
                }
                Files.createDirectories(output.getParent());
                try (var source = zip.getInputStream(entry)) {
                    Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void zip(Path directory, Path output) throws IOException {
        try (var out = IO.zipOut(output.toFile()); var files = Files.walk(directory)) {
            for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
                IO.writeEntry(
                    out, directory.relativize(file).toString().replace(File.separatorChar, '/'),
                    Files.readAllBytes(file)
                );
            }
        }
    }

}
