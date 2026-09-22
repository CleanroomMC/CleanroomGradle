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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplyBinPatchesTest {

    private static final String PREFIX = "binpatch/client/";

    @TempDir
    Path directory;

    @Test
    void patchesAddsAndRemovesUnderThePrefix() throws IOException {
        var original = classBytes("demo/A", 1);
        var revised = classBytes("demo/A", 2);
        var jar = writeZip("original.jar", Map.of("demo/A.class", original, "demo/Remove.class", classBytes("demo/Remove", 1)));
        var patches = writeZip(
                "binpatches.zip",
                Map.of(
                        PREFIX + "demo/A.class.binpatch",
                        patch(original, original, revised),
                        PREFIX + "demo/Added.class.add",
                        classBytes("demo/Added", 1),
                        PREFIX + "META-INF/binpatch-removed.txt",
                        "demo/Remove.class\n".getBytes(StandardCharsets.UTF_8),
                        "binpatch/server/demo/Ignored.class.add",
                        classBytes("demo/Ignored", 1)
                )
        );

        var output = this.directory.resolve("patched.jar");
        var result = ApplyBinPatches.apply(jar, patches, PREFIX, output);

        assertThat(result.patched()).isEqualTo(1);
        assertThat(result.added()).isEqualTo(1);
        assertThat(result.removed()).isEqualTo(1);
        var entries = readZip(output);
        assertThat(entries).containsOnlyKeys("demo/A.class", "demo/Added.class");
        assertThat(entries.get("demo/A.class")).isEqualTo(revised);
    }

    @Test
    void refusesPatchesThatDoNotFitTheJar() throws IOException {
        var original = classBytes("demo/A", 1);
        var jar = writeZip("original.jar", Map.of("demo/A.class", original));

        assertRejected(jar, "demo/Ghost.class.binpatch", patch(original, original, original), "absent");
        assertRejected(jar, "demo/A.class.binpatch", patch(new byte[] { 1 }, original, classBytes("demo/A", 2)), "SHA-256 mismatch");
        assertRejected(jar, "demo/A.class.binpatch", new byte[4], "truncated");
    }

    private void assertRejected(Path jar, String entry, byte[] patch, String message) throws IOException {
        var patches = writeZip("rejected.zip", Map.of(entry, patch, "META-INF/binpatch-removed.txt", new byte[0]));

        assertThatThrownBy(() -> ApplyBinPatches.apply(jar, patches, "", this.directory.resolve("rejected.jar")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message);
    }

    private static byte[] patch(byte[] base, byte[] original, byte[] revised) {
        var hash = IO.sha256(base);
        var delta = BinDelta.encode(original, revised);
        var result = Arrays.copyOf(hash, hash.length + delta.length);
        System.arraycopy(delta, 0, result, hash.length, delta.length);
        return result;
    }

    private static byte[] classBytes(String name, int marker) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "marker" + marker, "I", null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private Path writeZip(String name, Map<String, byte[]> entries) throws IOException {
        var path = this.directory.resolve(name);
        try (var out = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : new LinkedHashMap<>(entries).entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return path;
    }

    private static Map<String, byte[]> readZip(Path path) throws IOException {
        var result = new HashMap<String, byte[]>();
        try (var zip = new ZipFile(path.toFile())) {
            for (var entry : zip.stream().filter(entry -> !entry.isDirectory()).toList()) {
                try (var in = zip.getInputStream(entry)) {
                    result.put(entry.getName(), in.readAllBytes());
                }
            }
        }
        return result;
    }

}
