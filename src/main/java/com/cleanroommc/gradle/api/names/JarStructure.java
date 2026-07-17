package com.cleanroommc.gradle.api.names;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The class/method/field shape of a jar, read with ASM. Used to emit valid Tiny2
 * (which needs real descriptors that the legacy MCP SRG text files do not carry, especially for fields).
 */
public record JarStructure(Map<String, ClassEntry> classes) {

    /** A method or field: its name and JVM descriptor. */
    public record Member(String name, String descriptor) { }

    /** One class's SRG-named members. */
    public record ClassEntry(String internalName, List<Member> methods, List<Member> fields) { }

    /** Scans a jar, keeping only members whose names look like SRG ids (func_/field_). */
    public static JarStructure scan(File jarFile) {
        var classes = new LinkedHashMap<String, ClassEntry>();
        try (var zip = new ZipFile(jarFile)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(in);
                    Collector collector = new Collector();
                    reader.accept(collector, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (!collector.methods.isEmpty() || !collector.fields.isEmpty()) {
                        classes.put(collector.internalName, new ClassEntry(collector.internalName, collector.methods, collector.fields));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan jar " + jarFile, e);
        }
        return new JarStructure(classes);
    }

    private static final class Collector extends ClassVisitor {

        private final List<Member> methods = new ArrayList<>();
        private final List<Member> fields = new ArrayList<>();

        private String internalName;

        Collector() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.internalName = name;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (name.startsWith("func_")) {
                methods.add(new Member(name, descriptor));
            }
            return null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (name.startsWith("field_")) {
                fields.add(new Member(name, descriptor));
            }
            return null;
        }

    }
}
