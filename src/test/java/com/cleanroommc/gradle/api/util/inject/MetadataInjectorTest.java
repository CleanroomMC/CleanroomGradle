/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util.inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataInjectorTest {

    private static final String OWNER = "net/minecraft/Demo";

    @TempDir
    Path directory;

    @Test
    void injectsNamesVisibilitiesAndExceptions() throws Exception {
        var input = this.directory.resolve("in.jar");
        var output = this.directory.resolve("out.jar");
        var untouched = "sample".getBytes(StandardCharsets.UTF_8);
        writeJar(input, Map.of(OWNER + ".class", demoClass(), "other/Ignored.class", demoClass(), "META-INF/MANIFEST.MF", untouched));

        var access = this.write("access.txt", "PUBLIC " + OWNER + " func_1234_a (ILjava/lang/String;)V");
        var constructors = this.write("constructors.txt", "77 " + OWNER + " (Ljava/lang/String;)V");
        var exceptions = this.write("exceptions.txt", OWNER + "/func_1234_a (ILjava/lang/String;)V java/io/IOException");

        var result = MetadataInjector.inject(input, output, access, constructors, exceptions);

        assertThat(result.classesProcessed()).isEqualTo(1);
        assertThat(result.entriesCopied()).isEqualTo(2);
        assertThat(result.abstractMethodsRecorded()).isEqualTo(1);

        var entries = readJar(output);
        assertThat(entries.get("META-INF/MANIFEST.MF")).isEqualTo(untouched);
        assertThat(entries.get("other/Ignored.class")).as("classes outside net/minecraft are copied").isEqualTo(demoClass());

        var node = read(entries.get(OWNER + ".class"));
        var named = method(node, "func_1234_a");
        assertThat(named.access & 0b111).as("access.txt raises the method to public").isEqualTo(Opcodes.ACC_PUBLIC);
        assertThat(named.exceptions).isEqualTo(java.util.List.of("java/io/IOException"));
        assertThat(local(named, 0).name).isEqualTo("this");
        assertThat(local(named, 1).name).isEqualTo("p_1234_1_");
        assertThat(local(named, 2).name).isEqualTo("p_1234_2_");
        assertThat(local(named, 3).name).as("snowman placeholders are renamed after their slot").isEqualTo("lvt_3_1_");

        var constructor = method(node, "<init>");
        assertThat(local(constructor, 1).name).as("constructors.txt supplies the id").isEqualTo("p_i77_1_");

        var abstractNames = new String(entries.get("fernflower_abstract_parameter_names.txt"), StandardCharsets.UTF_8);
        assertThat(abstractNames).as("`this` is not written for abstract methods").isEqualTo(OWNER + " func_5678_b (J)V p_5678_1_\n");
    }

    @Test
    void allocatesMissingConstructorIdsInJarOrder() throws Exception {
        var input = this.directory.resolve("ctor-in.jar");
        var output = this.directory.resolve("ctor-out.jar");
        var ordered = new LinkedHashMap<String, byte[]>();
        ordered.put("net/minecraft/B.class", constructorOnlyClass("net/minecraft/B"));
        ordered.put("net/minecraft/C.class", constructorOnlyClass("net/minecraft/C"));
        writeJar(input, ordered);

        var constructors = this.write("constructors.txt", "5 net/minecraft/A (Ljava/lang/String;)V");
        MetadataInjector.inject(input, output, this.write("access.txt", ""), constructors, this.write("exceptions.txt", ""));

        var entries = readJar(output);
        assertThat(local(method(read(entries.get("net/minecraft/B.class")), "<init>"), 1).name).isEqualTo("p_i6_1_");
        assertThat(local(method(read(entries.get("net/minecraft/C.class")), "<init>"), 1).name)
                .as("ids follow jar order, whichever thread happened to reach the class first")
                .isEqualTo("p_i7_1_");
    }

    @Test
    void producesTheSameBytesEveryRun() throws Exception {
        var input = this.directory.resolve("repeat-in.jar");
        var ordered = new LinkedHashMap<String, byte[]>();
        ordered.put(OWNER + ".class", demoClass());
        ordered.put("net/minecraft/B.class", constructorOnlyClass("net/minecraft/B"));
        writeJar(input, ordered);
        var access = this.write("access.txt", "");
        var constructors = this.write("constructors.txt", "");
        var exceptions = this.write("exceptions.txt", "");

        var first = this.directory.resolve("first.jar");
        var second = this.directory.resolve("second.jar");
        MetadataInjector.inject(input, first, access, constructors, exceptions);
        MetadataInjector.inject(input, second, access, constructors, exceptions);

        assertThat(Files.readAllBytes(second)).isEqualTo(Files.readAllBytes(first));
    }

    private static byte[] constructorOnlyClass(String owner) {
        var node = new ClassNode();
        node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, owner, null, "java/lang/Object", null);
        var constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        constructor.maxLocals = 2;
        node.methods.add(constructor);
        var writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private Path write(String name, String content) throws Exception {
        var file = this.directory.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private static byte[] demoClass() {
        var node = new ClassNode();
        node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER, OWNER, null, "java/lang/Object", null);

        var named = new MethodNode(Opcodes.ACC_PRIVATE, "func_1234_a", "(ILjava/lang/String;)V", null, null);
        var start = new LabelNode();
        var end = new LabelNode();
        named.instructions.add(start);
        named.instructions.add(new InsnNode(Opcodes.RETURN));
        named.instructions.add(end);
        named.localVariables.add(new LocalVariableNode("☃", "I", null, start, end, 3));
        named.maxLocals = 4;
        node.methods.add(named);

        var constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        constructor.maxLocals = 2;
        node.methods.add(constructor);

        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "func_5678_b", "(J)V", null, null));

        var writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static LocalVariableNode local(MethodNode method, int slot) {
        if (method.localVariables == null) {
            return null;
        }
        return method.localVariables.stream().filter(local -> local.index == slot).findFirst().orElse(null);
    }

    private static ClassNode read(byte[] data) {
        var node = new ClassNode();
        new ClassReader(data).accept(node, 0);
        return node;
    }

    private static void writeJar(Path file, Map<String, byte[]> entries) throws Exception {
        try (var out = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : new LinkedHashMap<>(entries).entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
    }

    private static Map<String, byte[]> readJar(Path file) throws Exception {
        var entries = new LinkedHashMap<String, byte[]>();
        try (var zip = new ZipFile(file.toFile())) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                var entry = iterator.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                try (var stream = zip.getInputStream(entry)) {
                    entries.put(entry.getName(), stream.readAllBytes());
                }
            }
        }
        return entries;
    }

}
