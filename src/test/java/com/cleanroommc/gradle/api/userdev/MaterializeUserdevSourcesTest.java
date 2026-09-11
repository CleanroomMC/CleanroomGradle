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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The patch step decides whether a workspace's sources are complete, so it has to fail rather than skip,
 * and it has to write the same bytes on every platform for the transform's output to be cacheable.
 */
class MaterializeUserdevSourcesTest {

    @TempDir
    Path directory;

    @Test
    void appliesAPatchWithLineFeedsOnly() throws IOException {
        var source = write("source/net/minecraft/Block.java", "class Block {\nvoid a() { }\n}\n");
        write(
                "patches/net/minecraft/Block.java.patch",
                """
                --- a/net/minecraft/Block.java
                +++ b/net/minecraft/Block.java
                @@ -1,3 +1,3 @@
                 class Block {
                -void a() { }
                +void b() { }
                 }
                """
        );

        UserdevSourceMaterializer.applyPatches(this.directory.resolve("source"), this.directory.resolve("patches"));

        assertThat(Files.readString(source, StandardCharsets.UTF_8)).isEqualTo("class Block {\nvoid b() { }\n}\n");
    }

    @Test
    void failsWhenAPatchHasNoTarget() throws IOException {
        write("source/net/minecraft/Block.java", "class Block {\n}\n");
        write(
                "patches/net/minecraft/Missing.java.patch",
                """
                --- a/net/minecraft/Missing.java
                +++ b/net/minecraft/Missing.java
                @@ -1,1 +1,1 @@
                -class Missing { }
                +class Missing { void a() { } }
                """
        );

        var failure = catchThrowableOfType(
                () -> UserdevSourceMaterializer.applyPatches(this.directory.resolve("source"), this.directory.resolve("patches")),
                IllegalStateException.class
        );
        assertThat(failure).as(failure.getMessage()).hasMessageContaining("Missing.java.patch");
        assertThat(failure.getMessage().contains("net" + java.io.File.separator + "minecraft" + java.io.File.separator + "Missing.java"))
                .as(failure.getMessage())
                .isTrue();
    }

    private Path write(String path, String content) throws IOException {
        var file = this.directory.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

}
