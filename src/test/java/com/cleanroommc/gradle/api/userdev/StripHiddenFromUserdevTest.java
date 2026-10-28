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

import com.cleanroommc.gradle.api.util.IO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class StripHiddenFromUserdevTest {

    private static final String ANNOTATION = "HideFromUserdev";
    private static final String HIDE = "Lcom/example/" + ANNOTATION + ";";

    @TempDir Path directory;

    private static byte[] kept() {
        var node = new ClassNode();
        node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "api/Kept", null, "java/lang/Object", null);
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "open", "I", null, null));
        var secret = new FieldNode(Opcodes.ACC_PUBLIC, "secret", "I", null, null);
        secret.visitAnnotation(HIDE, false);
        node.fields.add(secret);
        node.methods.add(method("<init>", null, false, Opcodes.ALOAD, 0));
        node.methods.add(method("run", null, false, Opcodes.NOP, -1));
        node.methods.add(method("hook", HIDE, true, Opcodes.NOP, -1));
        var writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode method(String name, String annotation, boolean visible, int opcode, int local) {
        var method = new MethodNode(Opcodes.ACC_PUBLIC, name, "()V", null, null);
        if (annotation != null) {
            method.visitAnnotation(annotation, visible);
        }
        method.visitCode();
        if (local >= 0) {
            method.visitVarInsn(opcode, local);
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        } else {
            method.visitInsn(opcode);
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        return method;
    }

    private static byte[] type(String name, String annotation, boolean visible) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        if (annotation != null) {
            writer.visitAnnotation(annotation, visible).visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static List<String> names(File jar) throws IOException {
        try (var zip = new ZipFile(jar)) {
            return zip.stream().map(ZipEntry::getName).toList();
        }
    }

    private static ClassNode node(File jar, String entry) throws IOException {
        try (var zip = new ZipFile(jar)) {
            var node = new ClassNode();
            new ClassReader(IO.readEntry(zip, zip.getEntry(entry))).accept(node, 0);
            return node;
        }
    }

    @Test
    void stripsMarkedMembersClassesAndPackages() throws IOException {
        var entries = new LinkedHashMap<String, byte[]>();
        entries.put("api/Kept.class", kept());
        entries.put("api/Hidden.class", type("api/Hidden", HIDE, false));
        entries.put("api/Hidden$Inner.class", type("api/Hidden$Inner", null, false));
        entries.put("api/Outer.class", type("api/Outer", null, false));
        entries.put("api/Outer$Secret.class", type("api/Outer$Secret", "Lzone/HideFromUserdev;", true));
        entries.put("api/Outer$Secret$Deep.class", type("api/Outer$Secret$Deep", null, false));
        entries.put("api/Outer$Open.class", type("api/Outer$Open", null, false));
        entries.put("api/Lookalike.class", type("api/Lookalike", "Lcom/example/NotHideFromUserdev;", false));
        entries.put("internal/package-info.class", type("internal/package-info", HIDE, false));
        entries.put("internal/Impl.class", type("internal/Impl", null, false));
        entries.put("internal/sub/More.class", type("internal/sub/More", null, false));
        entries.put("internal/data.txt", "kept".getBytes(StandardCharsets.UTF_8));
        entries.put("internalother/Thing.class", type("internalother/Thing", null, false));
        var input = jar("input.jar", entries);
        var output = this.directory.resolve("output.jar").toFile();

        assertThat(StripHiddenFromUserdev.strip(input, () -> output, ANNOTATION)).isTrue();

        assertThat(names(output)).containsExactly(
            "api/Kept.class", "api/Outer.class", "api/Outer$Open.class", "api/Lookalike.class", "internal/data.txt",
            "internalother/Thing.class"
        );
        var stripped = node(output, "api/Kept.class");
        assertThat(stripped.fields).extracting(field -> field.name).containsExactly("open");
        assertThat(stripped.methods).extracting(method -> method.name).containsExactly("<init>", "run");
    }

    @Test
    void leavesAJarWithNothingHiddenAlone() throws IOException {
        var input = jar("input.jar", Map.of("api/Open.class", type("api/Open", null, false)));

        assertThat(StripHiddenFromUserdev.strip(input, () -> {
            throw new AssertionError("nothing hidden, yet an output was asked for");
        }, ANNOTATION)).isFalse();
    }

    @Test
    void stripsSourcesTheSameWay() throws IOException {
        source("api/Kept.java", """
            package api;

            import com.example.HideFromUserdev;

            public class Kept {

                public int open;

                /** Hidden field. */
                @HideFromUserdev
                public int secret;

                @com.example.HideFromUserdev public int first, second;

                public void run() { }

                /**
                 * Hidden hook.
                 */
                @HideFromUserdev
                public void hook() {
                    int local = 0;
                }

                @HideFromUserdev
                public static class Secret {
                    public void inside() { }
                }

                /* kept comment */
                @NotHideFromUserdev
                public void lookalike() { }

            }
            """);
        source("api/Hidden.java", "package api;\n\n@HideFromUserdev\nclass Hidden { }\n");
        source("internal/package-info.java", "@HideFromUserdev\npackage internal;\n");
        source("internal/Impl.java", "package internal;\n\nclass Impl { }\n");
        source("internal/sub/More.java", "package internal.sub;\n\nclass More { }\n");
        source("internalother/Thing.java", "package internalother;\n\nclass Thing { }\n");

        StripHiddenFromUserdev.stripSources(this.directory, ANNOTATION);

        try (var files = Files.walk(this.directory)) {
            assertThat(files.filter(Files::isRegularFile).map(file -> this.directory.relativize(file).toString()))
                .containsExactlyInAnyOrder("api/Kept.java", "internalother/Thing.java");
        }
        assertThat(Files.readString(this.directory.resolve("api/Kept.java"))).isEqualTo("""
            package api;

            import com.example.HideFromUserdev;

            public class Kept {

                public int open;

                public void run() { }

                /* kept comment */
                @NotHideFromUserdev
                public void lookalike() { }

            }
            """);
    }

    private void source(String name, String content) throws IOException {
        var file = this.directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private File jar(String name, Map<String, byte[]> entries) throws IOException {
        var file = this.directory.resolve(name).toFile();
        try (var out = IO.zipOut(file)) {
            for (var entry : entries.entrySet()) {
                IO.writeEntry(out, entry.getKey(), entry.getValue());
            }
        }
        return file;
    }

}
