package com.cleanroommc.gradle.api.util.sas;

import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.RETURN;

class SideOnlyHandlerTest {

    @TempDir
    Path directory;

    @Test
    void stripRemovesDanglingNestMembers() throws Exception {
        var input = directory.resolve("in.jar");
        var output = directory.resolve("out.jar");
        writeJar(input, Map.of(
                "demo/Outer.class", outerWithNest("demo/Outer", "demo/Outer$Inner"),
                "demo/Outer$Inner.class", sideOnlyClass("demo/Outer$Inner", "demo/Outer", "Inner", Side.CLIENT)
        ));

        SideOnlyHandler.strip(input, output, Side.SERVER, true);

        var entries = readJar(output);
        assertTrue(entries.containsKey("demo/Outer.class"));
        assertFalse(entries.containsKey("demo/Outer$Inner.class"));
        var outer = readNode(entries.get("demo/Outer.class"));
        assertTrue(outer.nestMembers == null || outer.nestMembers.isEmpty());
        assertTrue(outer.innerClasses == null || outer.innerClasses.isEmpty());
    }

    @Test
    void stripRemovesAnonymousClassOnlyUsedByStrippedMethod() throws Exception {
        var input = directory.resolve("in.jar");
        var output = directory.resolve("out.jar");
        writeJar(input, Map.of(
                "demo/Host.class", hostWithSideOnlyFactory("demo/Host", "demo/Host$1", Side.CLIENT),
                "demo/Host$1.class", plainInner("demo/Host$1", "demo/Host")
        ));

        SideOnlyHandler.strip(input, output, Side.SERVER, true);

        var entries = readJar(output);
        assertTrue(entries.containsKey("demo/Host.class"));
        assertFalse(entries.containsKey("demo/Host$1.class"));
    }

    @Test
    void stripKeepsAnonymousClassUsedByRetainedMethod() throws Exception {
        var input = directory.resolve("in.jar");
        var output = directory.resolve("out.jar");
        writeJar(input, Map.of(
                "demo/Host.class", hostWithSideOnlyFactory("demo/Host", "demo/Host$1", null),
                "demo/Host$1.class", plainInner("demo/Host$1", "demo/Host")
        ));

        SideOnlyHandler.strip(input, output, Side.SERVER, true);

        var entries = readJar(output);
        assertTrue(entries.containsKey("demo/Host.class"));
        assertTrue(entries.containsKey("demo/Host$1.class"));
    }

    @Test
    void stripValidatesOnlyTheEntriesItIsPointedAt() throws Exception {
        var input = directory.resolve("in.jar");
        writeJar(input, Map.of(
                "loader/Host.class", hostWithSideOnlyFactory("loader/Host", "demo/Client", null),
                "demo/Client.class", sideOnlyClass("demo/Client", null, null, Side.CLIENT)
        ));

        assertThrows(IllegalStateException.class,
                () -> SideOnlyHandler.strip(input, directory.resolve("all.jar"), Side.SERVER, true));

        var output = directory.resolve("scoped.jar");
        SideOnlyHandler.strip(input, output, Side.SERVER, true, List.of("net/minecraft/"));

        var entries = readJar(output);
        assertTrue(entries.containsKey("loader/Host.class"));
        assertFalse(entries.containsKey("demo/Client.class"));
    }

    @Test
    void stripStillValidatesEntriesInsideThePrefix() throws Exception {
        var input = directory.resolve("in.jar");
        writeJar(input, Map.of(
                "net/minecraft/Host.class", hostWithSideOnlyFactory("net/minecraft/Host", "demo/Client", null),
                "demo/Client.class", sideOnlyClass("demo/Client", null, null, Side.CLIENT)
        ));

        assertThrows(IllegalStateException.class, () -> SideOnlyHandler.strip(
                input, directory.resolve("out.jar"), Side.SERVER, true, List.of("net/minecraft/")));
    }

    private static byte[] outerWithNest(String owner, String inner) {
        var node = emptyClass(owner);
        node.nestMembers = new ArrayList<>();
        node.nestMembers.add(inner);
        node.innerClasses.add(new InnerClassNode(inner, owner, "Inner", ACC_PUBLIC | ACC_STATIC));
        return write(node);
    }

    private static byte[] sideOnlyClass(String name, String outer, String inner, Side side) {
        var node = emptyClass(name);
        node.nestHostClass = outer;
        node.innerClasses.add(new InnerClassNode(name, outer, inner, ACC_PUBLIC | ACC_STATIC));
        node.visibleAnnotations = new ArrayList<>();
        node.visibleAnnotations.add(sideOnly(side));
        return write(node);
    }

    private static byte[] hostWithSideOnlyFactory(String owner, String inner, Side methodSide) {
        var node = emptyClass(owner);
        node.innerClasses.add(new InnerClassNode(inner, owner, null, 0));
        var ctor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions = new InsnList();
        ctor.instructions.add(new VarInsnNode(ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        ctor.instructions.add(new InsnNode(RETURN));
        ctor.maxStack = 1;
        ctor.maxLocals = 1;
        node.methods.add(ctor);
        var factory = new MethodNode(ACC_PUBLIC, "factory", "()V", null, null);
        if (methodSide != null) {
            factory.visibleAnnotations = new ArrayList<>();
            factory.visibleAnnotations.add(sideOnly(methodSide));
        }
        factory.instructions = new InsnList();
        factory.instructions.add(new TypeInsnNode(NEW, inner));
        factory.instructions.add(new InsnNode(DUP));
        factory.instructions.add(new MethodInsnNode(INVOKESPECIAL, inner, "<init>", "()V", false));
        factory.instructions.add(new InsnNode(POP));
        factory.instructions.add(new InsnNode(RETURN));
        factory.maxStack = 2;
        factory.maxLocals = 1;
        node.methods.add(factory);
        return write(node);
    }

    private static byte[] plainInner(String name, String outer) {
        var node = emptyClass(name);
        node.innerClasses.add(new InnerClassNode(name, outer, null, 0));
        var ctor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.instructions = new InsnList();
        ctor.instructions.add(new VarInsnNode(ALOAD, 0));
        ctor.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        ctor.instructions.add(new FrameNode(Opcodes.F_FULL, 1, new Object[] { name }, 0, new Object[0]));
        ctor.instructions.add(new InsnNode(RETURN));
        ctor.maxStack = 1;
        ctor.maxLocals = 1;
        node.methods.add(ctor);
        return write(node);
    }

    private static ClassNode emptyClass(String name) {
        var node = new ClassNode();
        node.version = Opcodes.V11;
        node.access = ACC_PUBLIC | ACC_SUPER;
        node.name = name;
        node.superName = "java/lang/Object";
        node.innerClasses = new ArrayList<>();
        node.methods = new ArrayList<>();
        node.fields = new ArrayList<>();
        node.interfaces = new ArrayList<>();
        return node;
    }

    private static AnnotationNode sideOnly(Side side) {
        var annotation = new AnnotationNode(SideOnlyHandler.SIDE_ONLY_DESCRIPTOR);
        annotation.values = new ArrayList<>();
        annotation.values.add("value");
        annotation.values.add(new String[] { "Lnet/minecraftforge/fml/relauncher/Side;", side.name() });
        return annotation;
    }

    private static byte[] write(ClassNode node) {
        var writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void writeJar(Path path, Map<String, byte[]> entries) throws Exception {
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }

    private static Map<String, byte[]> readJar(Path path) throws Exception {
        var result = new HashMap<String, byte[]>();
        try (var zip = new ZipFile(path.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                try (var stream = zip.getInputStream(entry)) {
                    result.put(entry.getName(), stream.readAllBytes());
                }
            }
        }
        return result;
    }

    private static ClassNode readNode(byte[] bytes) {
        var node = new ClassNode();
        new org.objectweb.asm.ClassReader(bytes).accept(node, 0);
        return node;
    }
}
