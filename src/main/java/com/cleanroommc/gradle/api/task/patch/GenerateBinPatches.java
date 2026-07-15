package com.cleanroommc.gradle.api.task.patch;

import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.binpatch.BinDelta;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Creates a deterministic archive containing class deltas between two jars at the same naming level.
 * Both jars must come out of the same compiler pipeline to ensure the most compact patches are generated.
 */
@CacheableTask
public abstract class GenerateBinPatches extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getOriginalJar();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getModifiedJar();

    @OutputFile
    public abstract RegularFileProperty getBinpatches();

    @TaskAction
    public void generate() {
        Path output = getBinpatches().getAsFile().get().toPath();
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        try {
            Map<String, byte[]> original = readClasses(getOriginalJar().getAsFile().get().toPath());
            Map<String, byte[]> modified = readClasses(getModifiedJar().getAsFile().get().toPath());
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            int changed = 0;
            int added = 0;
            try (var archive = IO.zipOut(temporary.toFile())) {
                for (Map.Entry<String, byte[]> entry : modified.entrySet()) {
                    String name = entry.getKey();
                    byte[] revised = entry.getValue();
                    byte[] base = original.get(name);
                    if (base == null) {
                        writeEntry(archive, name + ".add", revised);
                        added++;
                    } else if (!Arrays.equals(base, revised)) {
                        writeEntry(archive, name + ".binpatch", concatenate(sha256(base), BinDelta.encode(base, revised)));
                        changed++;
                    }
                }
                var removed = new TreeSet<>(original.keySet());
                removed.removeAll(modified.keySet());
                writeEntry(archive, "META-INF/binpatch-removed.txt", String.join("\n", removed).getBytes(StandardCharsets.UTF_8));
                getLogger().lifecycle("Binpatches: {} changed, {} added, {} removed -> {}", changed, added, removed.size(), output.getFileName());
            }
            moveIntoPlace(temporary, output);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw new UncheckedIOException("Failed to generate binpatches", e);
        }
    }

    private static Map<String, byte[]> readClasses(Path jar) throws IOException {
        Map<String, byte[]> classes = new TreeMap<>();
        try (var zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    classes.put(entry.getName(), input.readAllBytes());
                }
            }
        }
        return classes;
    }

    private static void writeEntry(ZipOutputStream output, String name, byte[] data) throws IOException {
        var entry = new ZipEntry(name);
        entry.setTime(0L);
        output.putNextEntry(entry);
        output.write(data);
        output.closeEntry();
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static void moveIntoPlace(Path temporary, Path output) throws IOException {
        try {
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

}
