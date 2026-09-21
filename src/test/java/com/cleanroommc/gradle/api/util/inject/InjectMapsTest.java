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

import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InjectMapsTest {

    @TempDir
    Path directory;

    @Test
    void accessLevelRoundTripsThroughOpcodes() {
        assertThat(AccessLevel.of(Opcodes.ACC_PRIVATE)).isEqualTo(AccessLevel.PRIVATE);
        assertThat(AccessLevel.of(Opcodes.ACC_PROTECTED)).isEqualTo(AccessLevel.PROTECTED);
        assertThat(AccessLevel.of(Opcodes.ACC_PUBLIC)).isEqualTo(AccessLevel.PUBLIC);
        assertThat(AccessLevel.of(0)).isEqualTo(AccessLevel.DEFAULT);

        assertThat((AccessLevel.PUBLIC.apply(0) & Opcodes.ACC_PUBLIC) != 0).isTrue();
        assertThat((AccessLevel.PRIVATE.apply(Opcodes.ACC_PUBLIC) & Opcodes.ACC_PRIVATE) != 0).isTrue();
        assertThat(AccessLevel.DEFAULT.apply(Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)).isEqualTo(0);
    }

    @Test
    void accessMapLoadsClassFieldAndMethodLevels() throws IOException {
        var file = write(
                "access.txt",
                List.of("# comment", "", "PUBLIC net/minecraft/Block", "PRIVATE net/minecraft/Block field_1_a", "PROTECTED net/minecraft/Block func_1_a ()V")
        );
        var map = AccessMap.load(file);
        assertThat(map.get("net/minecraft/Block").forClass()).isEqualTo(AccessLevel.PUBLIC);
        assertThat(map.get("net/minecraft/Block").forField("field_1_a")).isEqualTo(AccessLevel.PRIVATE);
        assertThat(map.get("net/minecraft/Block").forMethod("func_1_a", "()V")).isEqualTo(AccessLevel.PROTECTED);
        assertThat(map.get("missing/Class")).isNull();
    }

    @Test
    void accessMapRejectsMalformedLines() throws IOException {
        var file = write("bad-access.txt", List.of("PUBLIC"));
        assertThatThrownBy(() -> AccessMap.load(file)).isInstanceOf(IOException.class).hasMessageContaining("Malformed access line");
    }

    @Test
    void constructorMapLoadsGeneratesAndRejectsBadIds() throws IOException {
        var file = write("constructors.txt", List.of("# comment", "3 net/minecraft/Block (I)V", "7 net/minecraft/Block (Ljava/lang/String;)V"));
        var map = ConstructorMap.load(file);
        assertThat(map.get("net/minecraft/Block", "(I)V")).isEqualTo(3);
        assertThat(map.get("net/minecraft/Block", "()V")).isEqualTo(-1);

        var generated = map.generate("net/minecraft/Block", "()V");
        assertThat(generated).isEqualTo(8);
        assertThat(map.get("net/minecraft/Block", "()V")).isEqualTo(8);

        var bad = write("bad-constructors.txt", List.of("only-two parts"));
        assertThatThrownBy(() -> ConstructorMap.load(bad)).isInstanceOf(IOException.class).hasMessageContaining("Malformed constructor line");
    }

    @Test
    void exceptionMapLoadsOwnersAndRejectsMalformedLines() throws IOException {
        var file = write("exceptions.txt", List.of("net/minecraft/Block/func_1_a ()V java/io/IOException java/lang/Exception"));
        var map = ExceptionMap.load(file);
        assertThat(map.get("net/minecraft/Block").get("func_1_a ()V")).isEqualTo(new String[] { "java/io/IOException", "java/lang/Exception" });
        assertThat(map.get("missing/Class")).isNull();

        var bad = write("bad-exceptions.txt", List.of("no-spaces-here"));
        assertThatThrownBy(() -> ExceptionMap.load(bad)).isInstanceOf(IOException.class).hasMessageContaining("Malformed exceptions line");
    }

    private Path write(String name, List<String> lines) throws IOException {
        var file = this.directory.resolve(name);
        Files.write(file, lines);
        return file;
    }

}
