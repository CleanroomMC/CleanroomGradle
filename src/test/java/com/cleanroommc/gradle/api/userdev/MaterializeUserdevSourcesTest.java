package com.cleanroommc.gradle.api.userdev;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        write("patches/net/minecraft/Block.java.patch", """
                --- a/net/minecraft/Block.java
                +++ b/net/minecraft/Block.java
                @@ -1,3 +1,3 @@
                 class Block {
                -void a() { }
                +void b() { }
                 }
                """);

        UserdevSourceMaterializer.applyPatches(this.directory.resolve("source"), this.directory.resolve("patches"));

        assertEquals("class Block {\nvoid b() { }\n}\n", Files.readString(source, StandardCharsets.UTF_8));
    }

    @Test
    void failsWhenAPatchHasNoTarget() throws IOException {
        write("source/net/minecraft/Block.java", "class Block {\n}\n");
        write("patches/net/minecraft/Missing.java.patch", """
                --- a/net/minecraft/Missing.java
                +++ b/net/minecraft/Missing.java
                @@ -1,1 +1,1 @@
                -class Missing { }
                +class Missing { void a() { } }
                """);

        var failure = assertThrows(IllegalStateException.class, () -> UserdevSourceMaterializer
                .applyPatches(this.directory.resolve("source"), this.directory.resolve("patches")));
        assertTrue(failure.getMessage().contains("Missing.java.patch"), failure.getMessage());
        assertTrue(failure.getMessage().contains("net" + java.io.File.separator + "minecraft"
                + java.io.File.separator + "Missing.java"), failure.getMessage());
    }

    private Path write(String path, String content) throws IOException {
        var file = this.directory.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

}
