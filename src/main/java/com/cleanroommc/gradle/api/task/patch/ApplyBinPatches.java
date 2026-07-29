package com.cleanroommc.gradle.api.task.patch;

import com.cleanroommc.gradle.api.util.IO;
import com.cleanroommc.gradle.api.util.binpatch.BinDelta;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Reconstructs a patched jar out of an original jar and a {@link GenerateBinPatches} archive.
 *
 * <p>Every changed class is checked against the SHA-256 recorded in its patch
 * and a mismatch fails the build rather than producing a jar that is quietly wrong.</p>
 */
@CacheableTask
public abstract class ApplyBinPatches extends DefaultTask {

    private static final String BINPATCH_SUFFIX = ".binpatch";
    private static final String ADDED_SUFFIX = ".add";
    private static final String REMOVED_ENTRY = "META-INF/binpatch-removed.txt";
    private static final int SHA256_LENGTH = 32;

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getOriginalJar();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getBinpatches();

    /** Prefix the patch entries sit behind inside the archive, e.g. {@code binpatch/client/}. */
    @Input
    public abstract Property<String> getPrefix();

    @OutputFile
    public abstract RegularFileProperty getPatchedJar();

    public ApplyBinPatches() {
        getPrefix().convention("");
    }

    @TaskAction
    public void apply() {
        Path original = getOriginalJar().getAsFile().get().toPath();
        Path output = getPatchedJar().getAsFile().get().toPath();
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        try {
            var patches = readPatches();
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            int patched = 0;
            int removed = 0;
            var seen = new HashSet<String>();
            Map<String, byte[]> contents = new TreeMap<>();
            try (var zip = new ZipFile(original.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String name = entry.getName();
                    if (patches.removed().contains(name)) {
                        removed++;
                        continue;
                    }
                    byte[] data;
                    try (InputStream input = zip.getInputStream(entry)) {
                        data = input.readAllBytes();
                    }
                    byte[] delta = patches.deltas().get(name);
                    if (delta != null) {
                        data = patch(name, data, delta);
                        seen.add(name);
                        patched++;
                    }
                    contents.put(name, data);
                }
            }
            var missing = new LinkedHashSet<>(patches.deltas().keySet());
            missing.removeAll(seen);
            if (!missing.isEmpty()) {
                throw new IllegalStateException(("%d class(es) the binpatches change are absent from %s, " +
                        "e.g. %s.The original jar does not match the one the patches were generated against.")
                                .formatted(missing.size(), original.getFileName(), missing.getFirst()));
            }
            contents.putAll(patches.added());
            try (var archive = IO.zipOut(temporary.toFile())) {
                for (var entry : contents.entrySet()) {
                    writeEntry(archive, entry.getKey(), entry.getValue());
                }
            }
            getLogger().lifecycle("Binpatches: {} patched, {} added, {} removed -> {}",
                    patched, patches.added().size(), removed, output.getFileName());
            moveIntoPlace(temporary, output);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw new UncheckedIOException("Failed to apply binpatches to " + original, e);
        }
    }

    private Patches readPatches() throws IOException {
        String prefix = getPrefix().get();
        Map<String, byte[]> deltas = new TreeMap<>();
        Map<String, byte[]> added = new TreeMap<>();
        Set<String> removed = new HashSet<>();
        try (var zip = new ZipFile(getBinpatches().getAsFile().get())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(prefix)) {
                    continue;
                }
                String name = entry.getName().substring(prefix.length());
                byte[] data;
                try (InputStream input = zip.getInputStream(entry)) {
                    data = input.readAllBytes();
                }
                if (name.equals(REMOVED_ENTRY)) {
                    for (String line : new String(data, StandardCharsets.UTF_8).split("\n")) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            removed.add(trimmed);
                        }
                    }
                } else if (name.endsWith(BINPATCH_SUFFIX)) {
                    deltas.put(name.substring(0, name.length() - BINPATCH_SUFFIX.length()), data);
                } else if (name.endsWith(ADDED_SUFFIX)) {
                    added.put(name.substring(0, name.length() - ADDED_SUFFIX.length()), data);
                }
            }
        }
        return new Patches(deltas, added, removed);
    }

    private static byte[] patch(String name, byte[] original, byte[] patch) {
        if (patch.length < SHA256_LENGTH) {
            throw new IllegalStateException("Binpatch for " + name + " is truncated.");
        }
        byte[] expected = Arrays.copyOf(patch, SHA256_LENGTH);
        byte[] actual = sha256(original);
        if (!Arrays.equals(expected, actual)) {
            throw new IllegalStateException(("SHA-256 mismatch for %s: " +
                    "the original class is not the one the binpatch was generated against. " +
                    "Delete the cached Minecraft jars and let them re-download, " +
                    "and make sure the userdev artifact targets this Minecraft version.").formatted(name));
        }
        return BinDelta.decode(original, Arrays.copyOfRange(patch, SHA256_LENGTH, patch.length));
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

    private static void moveIntoPlace(Path temporary, Path output) throws IOException {
        try {
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record Patches(Map<String, byte[]> deltas, Map<String, byte[]> added, Set<String> removed) { }

}
