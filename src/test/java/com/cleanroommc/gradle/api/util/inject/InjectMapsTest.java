package com.cleanroommc.gradle.api.util.inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InjectMapsTest {

    @TempDir
    Path directory;

    @Test
    void accessLevelRoundTripsThroughOpcodes() {
        assertEquals(AccessLevel.PRIVATE, AccessLevel.of(Opcodes.ACC_PRIVATE));
        assertEquals(AccessLevel.PROTECTED, AccessLevel.of(Opcodes.ACC_PROTECTED));
        assertEquals(AccessLevel.PUBLIC, AccessLevel.of(Opcodes.ACC_PUBLIC));
        assertEquals(AccessLevel.DEFAULT, AccessLevel.of(0));

        assertTrue((AccessLevel.PUBLIC.apply(0) & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((AccessLevel.PRIVATE.apply(Opcodes.ACC_PUBLIC) & Opcodes.ACC_PRIVATE) != 0);
        assertEquals(0, AccessLevel.DEFAULT.apply(Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED));
    }

    @Test
    void accessMapLoadsClassFieldAndMethodLevels() throws IOException {
        var file = write("access.txt", List.of(
                "# comment",
                "",
                "PUBLIC net/minecraft/Block",
                "PRIVATE net/minecraft/Block field_1_a",
                "PROTECTED net/minecraft/Block func_1_a ()V"));
        var map = AccessMap.load(file);
        assertEquals(AccessLevel.PUBLIC, map.get("net/minecraft/Block").forClass());
        assertEquals(AccessLevel.PRIVATE, map.get("net/minecraft/Block").forField("field_1_a"));
        assertEquals(AccessLevel.PROTECTED, map.get("net/minecraft/Block").forMethod("func_1_a", "()V"));
        assertNull(map.get("missing/Class"));
    }

    @Test
    void accessMapRejectsMalformedLines() throws IOException {
        var file = write("bad-access.txt", List.of("PUBLIC"));
        assertTrue(assertThrows(IOException.class, () -> AccessMap.load(file))
                .getMessage().contains("Malformed access line"));
    }

    @Test
    void constructorMapLoadsGeneratesAndRejectsBadIds() throws IOException {
        var file = write("constructors.txt", List.of(
                "# comment",
                "3 net/minecraft/Block (I)V",
                "7 net/minecraft/Block (Ljava/lang/String;)V"));
        var map = ConstructorMap.load(file);
        assertEquals(3, map.get("net/minecraft/Block", "(I)V"));
        assertEquals(-1, map.get("net/minecraft/Block", "()V"));

        var generated = map.generate("net/minecraft/Block", "()V");
        assertEquals(8, generated);
        assertEquals(8, map.get("net/minecraft/Block", "()V"));

        var bad = write("bad-constructors.txt", List.of("only-two parts"));
        assertTrue(assertThrows(IOException.class, () -> ConstructorMap.load(bad))
                .getMessage().contains("Malformed constructor line"));
    }

    @Test
    void exceptionMapLoadsOwnersAndRejectsMalformedLines() throws IOException {
        var file = write("exceptions.txt", List.of(
                "net/minecraft/Block/func_1_a ()V java/io/IOException java/lang/Exception"));
        var map = ExceptionMap.load(file);
        assertArrayEquals(new String[] { "java/io/IOException", "java/lang/Exception" },
                map.get("net/minecraft/Block").get("func_1_a ()V"));
        assertNull(map.get("missing/Class"));

        var bad = write("bad-exceptions.txt", List.of("no-spaces-here"));
        assertTrue(assertThrows(IOException.class, () -> ExceptionMap.load(bad))
                .getMessage().contains("Malformed exceptions line"));
    }

    private Path write(String name, List<String> lines) throws IOException {
        var file = this.directory.resolve(name);
        Files.write(file, lines);
        return file;
    }

}
