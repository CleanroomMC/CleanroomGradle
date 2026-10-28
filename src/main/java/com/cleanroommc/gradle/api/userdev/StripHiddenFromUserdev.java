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

// The compiler tree API is exported by jdk.compiler, unlike the internals this rule targets
// CHECKSTYLE:OFF
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

// CHECKSTYLE:ON

import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.ZipFile;
import javax.tools.ToolProvider;

/**
 * Leaves out of the compile classpath whatever the loader marks with its hidden annotation, matched by simple name
 * in any package, so mods cannot compile against it while it still exists at runtime. A marked field or method
 * is removed from its class, a marked class is removed with its nested classes, and a marked package-info
 * removes its package along with every package under it. {@link #stripSources} applies the same rules to sources.
 */
@CacheableTransform
public abstract class StripHiddenFromUserdev implements TransformAction<StripHiddenFromUserdev.Parameters> {

    private static final String PACKAGE_INFO = "package-info";

    /**
     * Removes from the sources under {@code root}, in place, what {@link #strip} removes from their classes. A removed
     * declaration takes its javadoc with it, and a file left without a type is deleted.
     *
     * @param root       directory of {@code .java} files, laid out by package
     * @param annotation simple name of the annotation that hides what it marks
     */
    public static void stripSources(Path root, String annotation) throws IOException {
        List<Path> sources;
        try (var files = Files.walk(root)) {
            sources = files.filter(file -> file.toString().endsWith(".java")).toList();
        }
        List<Path> marked = new ArrayList<>();
        for (var file : sources) {
            if (Files.readString(file, StandardCharsets.UTF_8).contains(annotation)) {
                marked.add(file);
            }
        }
        if (marked.isEmpty()) {
            return;
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Stripping hidden sources needs Gradle to run on a JDK, not a JRE.");
        }
        List<Path> packages = new ArrayList<>();
        try (var fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(
                null, fileManager, _ -> { }, List.of("-proc:none"), null,
                fileManager.getJavaFileObjectsFromPaths(marked)
            );
            var positions = Trees.instance(task).getSourcePositions();
            for (var unit : task.parse()) {
                var file = Path.of(unit.getSourceFile().toUri());
                if (file.getFileName().toString().equals(PACKAGE_INFO + ".java")) {
                    if (hidden(unit.getPackageAnnotations(), annotation)) {
                        packages.add(file.getParent());
                    }
                    continue;
                }
                if (unit.getTypeDecls()
                    .stream()
                    .allMatch(type -> type instanceof ClassTree declared &&
                        hidden(declared.getModifiers().getAnnotations(), annotation))) {
                    Files.delete(file);
                    continue;
                }
                List<long[]> ranges = new ArrayList<>();
                new TreeScanner<Void, Void>() {

                    @Override
                    public Void visitClass(ClassTree tree, Void unused) {
                        return remove(tree, tree.getModifiers()) ? null : super.visitClass(tree, unused);
                    }

                    @Override
                    public Void visitMethod(MethodTree tree, Void unused) {
                        return remove(tree, tree.getModifiers()) ? null : super.visitMethod(tree, unused);
                    }

                    @Override
                    public Void visitVariable(VariableTree tree, Void unused) {
                        return remove(tree, tree.getModifiers()) ? null : super.visitVariable(tree, unused);
                    }

                    private boolean remove(Tree tree, ModifiersTree modifiers) {
                        if (!hidden(modifiers.getAnnotations(), annotation)) {
                            return false;
                        }
                        ranges.add(
                            new long[] { positions.getStartPosition(unit, tree), positions.getEndPosition(unit, tree) }
                        );
                        return true;
                    }

                }.scan(unit, null);
                if (ranges.isEmpty()) {
                    continue;
                }
                var text = new StringBuilder(unit.getSourceFile().getCharContent(true));
                ranges.sort(
                    Comparator.comparingLong((long[] range) -> range[0]).thenComparingLong(range -> range[1]).reversed()
                );
                var removedFrom = Long.MAX_VALUE;
                for (var range : ranges) {
                    // Every declarator of a field shares its modifiers, so the widest range covers the rest
                    if (range[0] >= removedFrom) {
                        continue;
                    }
                    removedFrom = range[0];
                    var end = (int) range[1];
                    var before = text.substring(0, (int) range[0]).stripTrailing();
                    var javadoc = before.lastIndexOf("/**");
                    if (before.endsWith("*/") &&
                        javadoc != -1 &&
                        before.indexOf("*/", javadoc) == before.length() - 2) {
                        before = before.substring(0, javadoc).stripTrailing();
                    }
                    text.delete(before.length(), end);
                }
                Files.writeString(file, text, StandardCharsets.UTF_8);
            }
        }
        for (var file : sources) {
            if (packages.stream().anyMatch(file::startsWith)) {
                Files.deleteIfExists(file);
            }
        }
    }

    /**
     * Writes the jar without what it hides, asking for {@code output} only when something is hidden.
     *
     * @return whether anything was hidden
     */
    static boolean strip(File input, Supplier<File> output, String annotation) throws IOException {
        try (var zip = new ZipFile(input)) {
            var entries = Collections.list(zip.entries());
            List<String> packages = new ArrayList<>();
            Set<String> classes = new HashSet<>();
            Map<String, byte[]> rewritten = new HashMap<>();
            for (var entry : entries) {
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                var bytes = IO.readEntry(zip, entry);
                if (!new String(bytes, StandardCharsets.ISO_8859_1).contains(annotation + ";")) {
                    continue;
                }
                var node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                if (hidden(node.visibleAnnotations, node.invisibleAnnotations, annotation)) {
                    if (node.name.endsWith("/" + PACKAGE_INFO)) {
                        packages.add(node.name.substring(0, node.name.length() - PACKAGE_INFO.length()));
                    } else {
                        classes.add(node.name);
                    }
                    continue;
                }
                var removed = node.fields.removeIf(field -> hidden(
                    field.visibleAnnotations, field.invisibleAnnotations, annotation
                ));
                removed |= node.methods.removeIf(method -> hidden(
                    method.visibleAnnotations, method.invisibleAnnotations, annotation
                ));
                if (removed) {
                    var writer = new ClassWriter(0);
                    node.accept(writer);
                    rewritten.put(entry.getName(), writer.toByteArray());
                }
            }
            if (packages.isEmpty() && classes.isEmpty() && rewritten.isEmpty()) {
                return false;
            }
            try (var out = IO.zipOut(output.get())) {
                for (var entry : entries) {
                    var name = entry.getName();
                    if (entry.isDirectory() || name.endsWith(".class") && hidden(name, packages, classes)) {
                        continue;
                    }
                    var bytes = rewritten.get(name);
                    IO.writeEntry(out, name, bytes == null ? IO.readEntry(zip, entry) : bytes);
                }
            }
            return true;
        }
    }

    private static boolean hidden(List<AnnotationNode> visible, List<AnnotationNode> invisible, String annotation) {
        return hidden(visible, annotation) || hidden(invisible, annotation);
    }

    private static boolean hidden(List<AnnotationNode> annotations, String annotation) {
        if (annotations != null) {
            for (var node : annotations) {
                if (node.desc.endsWith("/" + annotation + ";") || node.desc.equals("L" + annotation + ";")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hidden(Iterable<? extends AnnotationTree> annotations, String annotation) {
        for (var tree : annotations) {
            var type = tree.getAnnotationType().toString();
            if (type.equals(annotation) || type.endsWith("." + annotation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hidden(String entry, List<String> packages, Set<String> classes) {
        var name = entry.substring(0, entry.length() - ".class".length());
        for (var hiddenPackage : packages) {
            if (name.startsWith(hiddenPackage)) {
                return true;
            }
        }
        for (var nested = name.indexOf('$'); nested != -1; nested = name.indexOf('$', nested + 1)) {
            if (classes.contains(name.substring(0, nested))) {
                return true;
            }
        }
        return classes.contains(name);
    }

    @InputArtifact
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        var input = getInputArtifact().get().getAsFile();
        try {
            // Nothing is hidden, so the jar is handed on as it is instead of being copied
            if (!strip(input, () -> outputs.file(input.getName()), getParameters().getAnnotation().get())) {
                outputs.file(getInputArtifact());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to strip hidden members from " + input, e);
        }
    }

    public interface Parameters extends TransformParameters {

        @Input
        Property<String> getAnnotation();

    }

}
