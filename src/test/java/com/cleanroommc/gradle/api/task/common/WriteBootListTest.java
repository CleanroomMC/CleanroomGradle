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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.gradle.api.GradleException;
import org.gradle.testfixtures.ProjectBuilder;

import java.io.IOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.NestHostAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WriteBootListTest {

    private static final ClassDesc BOOT = ClassDesc.of("example.Boot");

    @TempDir Path directory;

    @Test
    void listsMarkedPackagesAndClassesWithTheirNestedClasses() throws IOException {
        write("boot/package-info", true, null);
        write("boot/Anything", false, null);
        write("game/package-info", false, null);
        write("game/Single", true, null);
        write("game/Single$Inner", false, "game/Single");
        write("game/SingleOther", false, null);
        write("game/Other", false, null);

        task().write();

        assertThat(Files.readAllLines(directory.resolve("out").resolve(WriteBootList.PATH))).containsExactly(
            "library a-1.0.jar", "library b-1.0.jar", "package boot", "class game/Single", "class game/Single$Inner",
            "resource log4j2.xml"
        );
    }

    @Test
    void refusesAMarkedNestedClass() throws IOException {
        write("game/Outer", false, null);
        write("game/Outer$Inner", true, "game/Outer");

        assertThatThrownBy(task()::write).isInstanceOf(GradleException.class).hasMessageContaining("game/Outer$Inner");
    }

    private void write(String name, boolean marked, String nestHost) throws IOException {
        byte[] bytes = ClassFile.of().build(ClassDesc.ofInternalName(name), builder -> {
            if (marked) {
                builder.with(RuntimeInvisibleAnnotationsAttribute.of(Annotation.of(BOOT)));
            }
            if (nestHost != null) {
                builder.with(NestHostAttribute.of(ClassDesc.ofInternalName(nestHost)));
            }
        });
        Path file = directory.resolve("classes").resolve(name + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private WriteBootList task() {
        var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        var task = project.getTasks().register("writeBootList", WriteBootList.class).get();
        task.getLibraries().set(List.of("b-1.0.jar", "a-1.0.jar"));
        task.getResources().set(List.of("log4j2.xml"));
        task.getAnnotation().set("example.Boot");
        task.getClasses().set(directory.resolve("classes").toFile());
        task.getOutput().set(directory.resolve("out").toFile());
        return task;
    }

}
