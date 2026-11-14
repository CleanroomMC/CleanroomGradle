/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.task.common;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.constant.ClassDesc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Writes {@code META-INF/cleanroom/boot.list}.
 * Boot packages and classes come from the {@link #getAnnotation() annotation}
 * on each {@code package-info} and class.
 */
@CacheableTask
public abstract class WriteBootList extends DefaultTask {

    public static final String PATH = "META-INF/cleanroom/boot.list";

    private static boolean marked(ClassModel model, String descriptor) {
        return model.findAttribute(Attributes.runtimeInvisibleAnnotations())
            .map(attribute -> attribute.annotations()
                .stream()
                .anyMatch(annotation -> annotation.classSymbol().descriptorString().equals(descriptor)))
            .orElse(false);
    }

    @Input
    public abstract ListProperty<String> getLibraries();

    @Input
    public abstract ListProperty<String> getResources();

    @Input
    public abstract Property<String> getAnnotation();

    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getClasses();

    @OutputDirectory
    public abstract DirectoryProperty getOutput();

    @TaskAction
    public void write() {
        Map<String, File> files = new TreeMap<>();
        getClasses()
            .getAsFileTree()
            .matching(filter -> filter.include("**/*.class"))
            .visit((FileVisitDetails details) -> {
                if (!details.isDirectory()) {
                    String path = details.getRelativePath().getPathString();
                    files.put(path.substring(0, path.length() - ".class".length()), details.getFile());
                }
            });

        String descriptor = ClassDesc.of(getAnnotation().get()).descriptorString();
        var packages = new TreeSet<String>();
        var marked = new ArrayList<String>();
        for (var entry : files.entrySet()) {
            String name = entry.getKey();
            ClassModel model;
            try {
                model = ClassFile.of().parse(entry.getValue().toPath());
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read " + entry.getValue(), e);
            }
            if (!marked(model, descriptor)) {
                continue;
            }
            if (name.endsWith("/package-info")) {
                packages.add(name.substring(0, name.lastIndexOf('/')));
            } else if (model.findAttribute(Attributes.nestHost()).isPresent()) {
                throw new GradleException(
                    "@" + getAnnotation().get() + " goes on top-level classes, " + name + " is nested"
                );
            } else {
                marked.add(name);
            }
        }
        var classes = new TreeSet<String>();
        for (String top : marked) {
            files.keySet()
                .stream()
                .filter(name -> name.equals(top) || name.startsWith(top + "$"))
                .forEach(classes::add);
        }

        List<String> lines = new ArrayList<>();
        getLibraries().get().stream().sorted().forEach(name -> lines.add("library " + name));
        packages.forEach(name -> lines.add("package " + name));
        classes.forEach(name -> lines.add("class " + name));
        getResources().get().forEach(name -> lines.add("resource " + name));

        var file = getOutput().file(PATH).get().getAsFile().toPath();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + file, e);
        }
    }

}
