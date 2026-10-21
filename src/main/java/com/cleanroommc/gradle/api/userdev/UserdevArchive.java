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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class UserdevArchive {

    private UserdevArchive() { }

    static void select(File input, File output, Predicate<String> include, String stripPrefix) {
        try (var zip = new ZipFile(input); var out = IO.zipOut(output)) {
            var entries = new TreeMap<String, ZipEntry>();
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                var entry = enumeration.nextElement();
                if (!entry.isDirectory() && include.test(entry.getName())) {
                    entries.put(entry.getName(), entry);
                }
            }
            for (var item : entries.entrySet()) {
                var name = item.getKey().substring(stripPrefix.length());
                if (!name.isEmpty()) {
                    IO.writeEntry(out, name, IO.readEntry(zip, item.getValue()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to materialize " + input, e);
        }
    }

    static File extract(File jar, String path, File output) throws IOException {
        try (var zip = new ZipFile(jar)) {
            var entry = zip.getEntry(path);
            if (entry == null) {
                throw new IllegalStateException("Missing required userdev entry " + path + ".");
            }
            Files.createDirectories(output.toPath().getParent());
            try (var source = zip.getInputStream(entry)) {
                Files.copy(source, output.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return output;
        }
    }

    static void delete(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var files = Files.walk(path)) {
            for (var file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    static void rename(
        ExecOperations exec, FileCollection classpath, File input, File output, File mappings, Iterable<File> libraries,
        String... flags) {
        var arguments = new ArrayList<>(List.of(
            "--input", input.getAbsolutePath(), "--map", mappings.getAbsolutePath(), "--output",
            output.getAbsolutePath()
        ));
        arguments.addAll(List.of(flags));
        for (var library : libraries) {
            arguments.add("--lib");
            arguments.add(library.getAbsolutePath());
        }
        Execs.quietJavaExec(exec, spec -> {
            spec.setClasspath(classpath);
            spec.getMainClass().set(mainClassOf(classpath));
            spec.setArgs(arguments);
        });
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

    private static String mainClassOf(Iterable<File> classpath) {
        for (var file : classpath) {
            try (var jar = new JarFile(file)) {
                var manifest = jar.getManifest();
                if (manifest != null) {
                    var main = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
                    if (main != null) {
                        return main;
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + file, e);
            }
        }
        throw new IllegalStateException("No Main-Class found on the renamer classpath.");
    }

}
