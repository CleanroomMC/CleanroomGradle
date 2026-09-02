package com.cleanroommc.gradle.api.task.patch;

import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.binpatch.BinDelta;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        Set<String> prefixes;
        try {
            prefixes = collectPrefixes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read obfuscation mappings", e);
        }
        try {
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (var archive = IO.zipOut(temporary.toFile())) {
                generateSide(archive, getClientOriginalJar().getAsFile().get(), getClientModifiedJar().getAsFile().get(),
                        getClientPrefix().get(), prefixes);
                generateSide(archive, getServerOriginalJar().getAsFile().get(), getServerModifiedJar().getAsFile().get(),
                        getServerPrefix().get(), prefixes);
            }
            IO.move(temporary, output);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw new UncheckedIOException("Failed to generate binpatches", e);
        }
    }

    private void generateSide(ZipOutputStream archive, File originalJar, File modifiedJar, String prefix,
                              Set<String> prefixes) throws IOException {
        int changed = 0;
        int added = 0;
        try (var originalZip = new ZipFile(originalJar); var modifiedZip = new ZipFile(modifiedJar)) {
            var original = indexClasses(originalZip, prefixes);
            var modified = indexClasses(modifiedZip, prefixes);
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
                        IO.writeEntry(archive, prefix + name + ".binpatch",
                                concatenate(IO.sha256(base), BinDelta.encode(base, revised)));
                        changed++;
                    }
                }
            }
            var removed = new TreeSet<>(original.keySet());
            removed.removeAll(modified.keySet());
            IO.writeEntry(archive, prefix + "META-INF/binpatch-removed.txt",
                    String.join("\n", removed).getBytes(StandardCharsets.UTF_8));
            getLogger().lifecycle("Binpatches {}: {} changed, {} added, {} removed", prefix, changed, added, removed.size());
        }
    }

    private Set<String> collectPrefixes() throws IOException {
        var prefixes = new TreeSet<>(getIncludedPrefixes().get());
        File mappings = getObfuscationMappings().getAsFile().getOrNull();
        if (mappings == null) {
            return prefixes;
        }
        for (String line : Files.readAllLines(mappings.toPath(), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || Character.isWhitespace(line.charAt(0))) {
                continue;
            }
            String obfuscatedName = line.split(" ", 2)[0];
            prefixes.add(obfuscatedName + ".class");
            prefixes.add(obfuscatedName + "$");
        }
        return prefixes;
    }

    private static Map<String, ZipEntry> indexClasses(ZipFile zip, Set<String> prefixes) {
        var classes = new TreeMap<String, ZipEntry>();
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            if (entry.isDirectory() || !entry.getName().endsWith(".class") || !included(entry.getName(), prefixes)) {
                continue;
            }
            classes.put(entry.getName(), entry);
        }
        return classes;
    }

    private static boolean included(String name, Set<String> prefixes) {
        if (prefixes.isEmpty()) {
            return true;
        }
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

}
