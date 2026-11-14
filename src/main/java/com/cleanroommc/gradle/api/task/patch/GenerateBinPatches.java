/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.task.patch;

import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.binpatch.BinDelta;
import com.cleanroommc.gradle.api.util.dist.MinecraftPackages;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Creates one deterministic archive containing the client and server class deltas between two jars at the
 * same naming level. Both jars of a side must come out of the same compiler pipeline to ensure the most
 * compact patches are generated. Each side's entries are written under its own prefix, which is what
 * {@link ApplyBinPatches} reads back.
 */
@CacheableTask
public abstract class GenerateBinPatches extends DefaultTask {

    private static Map<String, ZipEntry> indexClasses(ZipFile zip, Predicate<String> included) throws IOException {
        var classes = IO.sortedEntries(zip);
        classes.keySet().removeIf(name -> !name.endsWith(".class") || !included.test(name));
        return classes;
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getClientOriginalJar();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getClientModifiedJar();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getServerOriginalJar();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getServerModifiedJar();

    @Input
    public abstract Property<String> getClientPrefix();

    @Input
    public abstract Property<String> getServerPrefix();

    @Input
    public abstract SetProperty<String> getIncludedPrefixes();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getObfuscationMappings();

    @OutputFile
    public abstract RegularFileProperty getBinpatches();

    @TaskAction
    public void generate() {
        Path output = getBinpatches().getAsFile().get().toPath();
        Predicate<String> included;
        try {
            included = classFilter();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read obfuscation mappings", e);
        }
        try {
            var clientOriginal = getClientOriginalJar().getAsFile().get();
            var clientModified = getClientModifiedJar().getAsFile().get();
            var serverOriginal = getServerOriginalJar().getAsFile().get();
            var serverModified = getServerModifiedJar().getAsFile().get();
            IO.writeZip(output, archive -> {
                generateSide(archive, clientOriginal, clientModified, getClientPrefix().get(), included);
                generateSide(archive, serverOriginal, serverModified, getServerPrefix().get(), included);
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate binpatches", e);
        }
    }

    private void generateSide(
        ZipOutputStream archive, File originalJar, File modifiedJar, String prefix, Predicate<String> included
    ) throws IOException {
        int changed = 0;
        int added = 0;
        try (var originalZip = new ZipFile(originalJar); var modifiedZip = new ZipFile(modifiedJar)) {
            var original = indexClasses(originalZip, included);
            var modified = indexClasses(modifiedZip, included);
            for (var entry : modified.entrySet()) {
                String name = entry.getKey();
                byte[] revised = IO.readEntry(modifiedZip, entry.getValue());
                var originalEntry = original.get(name);
                if (originalEntry == null) {
                    IO.writeEntry(archive, prefix + name + ".add", revised);
                    added++;
                } else {
                    byte[] base = IO.readEntry(originalZip, originalEntry);
                    if (!Arrays.equals(base, revised)) {
                        IO.writeEntry(
                            archive, prefix + name + ".binpatch",
                            concatenate(IO.sha256(base), BinDelta.encode(base, revised))
                        );
                        changed++;
                    }
                }
            }
            var removed = new TreeSet<>(original.keySet());
            removed.removeAll(modified.keySet());
            IO.writeEntry(
                archive, prefix + "META-INF/binpatch-removed.txt",
                String.join("\n", removed).getBytes(StandardCharsets.UTF_8)
            );
            getLogger().lifecycle(
                "Binpatches {}: {} changed, {} added, {} removed", prefix, changed, added, removed.size()
            );
        }
    }

    private Predicate<String> classFilter() throws IOException {
        var prefixes = new TreeSet<>(getIncludedPrefixes().get());
        File mappings = getObfuscationMappings().getAsFile().getOrNull();
        if (mappings == null) {
            return name -> prefixes.isEmpty() || prefixes.stream().anyMatch(name::startsWith);
        }
        for (String line : Files.readAllLines(mappings.toPath(), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || Character.isWhitespace(line.charAt(0))) {
                continue;
            }
            String obfuscatedName = line.split(" ", 2)[0];
            prefixes.add(obfuscatedName + ".class");
            prefixes.add(obfuscatedName + "$");
        }
        var packages = MinecraftPackages.read(mappings.toPath());
        return name -> MinecraftPackages.contains(packages, name) || prefixes.stream().anyMatch(name::startsWith);
    }

}
