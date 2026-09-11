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
import com.cleanroommc.gradle.api.util.sas.SideOnlyHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class SasPatchTest {

    @TempDir
    Path directory;

    @Test
    void parseLineHandlesBlankCommentClassFieldAndMethod() {
        assertThat(SideOnlyHandler.parseLine("")).isNull();
        assertThat(SideOnlyHandler.parseLine("   ")).isNull();
        assertThat(SideOnlyHandler.parseLine("# only a comment")).isNull();
        assertThat(SideOnlyHandler.parseLine("\t# generated comment only")).isNull();

        var cls = SideOnlyHandler.parseLine("net.minecraft.Block");
        assertThat(cls.target().kind()).isEqualTo(SideOnlyHandler.TargetKind.CLASS);
        assertThat(cls.target().owner()).isEqualTo("net/minecraft/Block");
        assertThat(cls.generated()).isFalse();

        var dotted = SideOnlyHandler.parseLine("net.minecraft.Block # a block");
        assertThat(dotted.target().owner()).isEqualTo("net/minecraft/Block");
        assertThat(dotted.comment()).isEqualTo("a block");

        var generated = SideOnlyHandler.parseLine("\tnet.minecraft.Block");
        assertThat(generated.generated()).isTrue();

        var field = SideOnlyHandler.parseLine("net.minecraft.Block field_1_a");
        assertThat(field.target().kind()).isEqualTo(SideOnlyHandler.TargetKind.FIELD);
        assertThat(field.target().name()).isEqualTo("field_1_a");

        var method = SideOnlyHandler.parseLine("net.minecraft.Block func_1_a ()V");
        assertThat(method.target().kind()).isEqualTo(SideOnlyHandler.TargetKind.METHOD);
        assertThat(method.target().name()).isEqualTo("func_1_a");
        assertThat(method.target().descriptor()).isEqualTo("()V");
    }

    @Test
    void parseLineRejectsBadTargets() {
        assertThatThrownBy(() -> SideOnlyHandler.parseLine("net.minecraft.Block field one two")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SideOnlyHandler.parseLine("net.minecraft.Block ()V")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void targetFormatsAndSorts() {
        var cls = new SideOnlyHandler.Target(SideOnlyHandler.TargetKind.CLASS, "a.B", null, null);
        assertThat(cls.format()).isEqualTo("a/B");
        var field = new SideOnlyHandler.Target(SideOnlyHandler.TargetKind.FIELD, "a.B", "field_1_a", null);
        assertThat(field.format()).isEqualTo("a/B field_1_a");
        var method = new SideOnlyHandler.Target(SideOnlyHandler.TargetKind.METHOD, "a.B", "func_1_a", "()V");
        assertThat(method.format()).isEqualTo("a/B func_1_a()V");
        assertThatThrownBy(() -> new SideOnlyHandler.Target(SideOnlyHandler.TargetKind.CLASS, " ", null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applySasStripsAnnotationsAndFailsOnMissingTargets() throws IOException {
        var input = this.directory.resolve("in.jar");
        writeJar(input, Map.of("demo/Block.class", annotatedMethod("demo/Block", "func_1_a")));

        var output = this.directory.resolve("out.jar");
        var target = new SideOnlyHandler.Target(SideOnlyHandler.TargetKind.METHOD, "demo/Block", "func_1_a", "()V");
        var result = SideOnlyHandler.applySas(input, output, Set.of(target));
        assertThat(result.annotationsRemoved()).isEqualTo(1);

        var missing = new SideOnlyHandler.Target(SideOnlyHandler.TargetKind.METHOD, "demo/Block", "missing", "()V");
        var failure = catchThrowableOfType(
                () -> SideOnlyHandler.applySas(input, this.directory.resolve("out2.jar"), Set.of(missing)),
                IllegalArgumentException.class
        );
        assertThat(failure).as(failure.getMessage()).hasMessageContaining("did not resolve");
    }

    @Test
    void applyBinPatchesRoundTripsPatchedAddedAndRemoved() throws IOException {
        var originalBytes = classBytes("demo/A", 1);
        var revisedBytes = classBytes("demo/A", 2);
        var original = this.directory.resolve("original.jar");
        writeJar(original, Map.of("demo/A.class", originalBytes, "demo/Remove.class", classBytes("demo/R", 1)));

        var patches = this.directory.resolve("binpatches.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(patches))) {
            var delta = concatenate(IO.sha256(originalBytes), BinDelta.encode(originalBytes, revisedBytes));
            zipEntry(out, "demo/A.class.binpatch", delta);
            zipEntry(out, "demo/Added.class.add", classBytes("demo/Added", 9));
            zipEntry(out, "META-INF/binpatch-removed.txt", "demo/Remove.class\n".getBytes(StandardCharsets.UTF_8));
        }

        var output = this.directory.resolve("patched.jar");
        var result = ApplyBinPatches.apply(original, patches, "", output);
        assertThat(result.patched()).isEqualTo(1);
        assertThat(result.added()).isEqualTo(1);
        assertThat(result.removed()).isEqualTo(1);

        var entries = readJar(output);
        assertThat(Arrays.equals(revisedBytes, entries.get("demo/A.class"))).isTrue();
        assertThat(entries).containsKey("demo/Added.class");
        assertThat(entries).doesNotContainKey("demo/Remove.class");
    }

    @Test
    void applyBinPatchesHonorsPrefix() throws IOException {
        var originalBytes = classBytes("demo/A", 1);
        var revisedBytes = classBytes("demo/A", 2);
        var original = this.directory.resolve("original.jar");
        writeJar(original, Map.of("demo/A.class", originalBytes));
        var patches = this.directory.resolve("binpatches.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(patches))) {
            zipEntry(out, "binpatch/client/demo/A.class.binpatch", concatenate(IO.sha256(originalBytes), BinDelta.encode(originalBytes, revisedBytes)));
            zipEntry(out, "binpatch/client/META-INF/binpatch-removed.txt", new byte[0]);
        }
        var output = this.directory.resolve("patched.jar");
        var result = ApplyBinPatches.apply(original, patches, "binpatch/client/", output);
        assertThat(result.patched()).isEqualTo(1);
        assertThat(Arrays.equals(revisedBytes, readJar(output).get("demo/A.class"))).isTrue();
    }

    @Test
    void applyBinPatchesFailsOnMissingShaMismatchAndTruncation() throws IOException {
        var original = this.directory.resolve("original.jar");
        writeJar(original, Map.of("demo/A.class", classBytes("demo/A", 1)));

        var missingPatches = this.directory.resolve("missing.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(missingPatches))) {
            zipEntry(out, "demo/Ghost.class.binpatch", new byte[64]);
            zipEntry(out, "META-INF/binpatch-removed.txt", new byte[0]);
        }
        assertThatThrownBy(() -> ApplyBinPatches.apply(original, missingPatches, "", this.directory.resolve("o1.jar")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("absent");

        var wrongBase = this.directory.resolve("wrong.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(wrongBase))) {
            zipEntry(out, "demo/A.class.binpatch", concatenate(new byte[32], BinDelta.encode(classBytes("demo/A", 1), classBytes("demo/A", 2))));
            zipEntry(out, "META-INF/binpatch-removed.txt", new byte[0]);
        }
        assertThatThrownBy(() -> ApplyBinPatches.apply(original, wrongBase, "", this.directory.resolve("o2.jar")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHA-256 mismatch");

        var truncated = this.directory.resolve("truncated.zip");
        try (var out = new ZipOutputStream(Files.newOutputStream(truncated))) {
            zipEntry(out, "demo/A.class.binpatch", new byte[4]);
            zipEntry(out, "META-INF/binpatch-removed.txt", new byte[0]);
        }
        assertThatThrownBy(() -> ApplyBinPatches.apply(original, truncated, "", this.directory.resolve("o3.jar")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("truncated");
    }

    private static byte[] annotatedMethod(String owner, String name) {
        var node = new ClassNode();
        node.version = Opcodes.V11;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
        node.name = owner;
        node.superName = "java/lang/Object";
        var method = new MethodNode(Opcodes.ACC_PUBLIC, name, "()V", null, null);
        method.visibleAnnotations = new ArrayList<>(List.of(sideOnly(Side.CLIENT)));
        method.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
        method.maxStack = 0;
        method.maxLocals = 1;
        node.methods = new ArrayList<>(List.of(method));
        node.fields = new ArrayList<>();
        node.interfaces = new ArrayList<>();
        node.innerClasses = new ArrayList<>();
        var writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static AnnotationNode sideOnly(Side side) {
        var annotation = new AnnotationNode(SideOnlyHandler.SIDE_ONLY_DESCRIPTOR);
        annotation.values = new ArrayList<>();
        annotation.values.add("value");
        annotation.values.add(new String[] { "Lnet/minecraftforge/fml/relauncher/Side;", side.name() });
        return annotation;
    }

    private static byte[] classBytes(String name, int marker) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "marker" + marker, "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void writeJar(Path path, Map<String, byte[]> entries) throws IOException {
        try (var out = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                zipEntry(out, entry.getKey(), entry.getValue());
            }
        }
    }

    private static void zipEntry(ZipOutputStream out, String name, byte[] data) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(data);
        out.closeEntry();
    }

    private static Map<String, byte[]> readJar(Path path) throws IOException {
        var result = new HashMap<String, byte[]>();
        try (var zip = new ZipFile(path.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                try (var in = zip.getInputStream(entry)) {
                    result.put(entry.getName(), in.readAllBytes());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        var result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

}
