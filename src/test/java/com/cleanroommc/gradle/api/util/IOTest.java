package com.cleanroommc.gradle.api.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class IOTest {

    @Test
    void normalizeZipProducesIdenticalBytes(@TempDir Path directory) throws Exception {
        var first = directory.resolve("first.zip");
        var second = directory.resolve("second.zip");

        writeZip(first, 1_000L);
        writeZip(second, 2_000_000_000_000L);

        IO.normalizeZip(first);
        IO.normalizeZip(second);

        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    }

    private static void writeZip(Path path, long timestamp) throws Exception {
        try (var output = IO.zipOut(path.toFile())) {
            writeEntry(output, "z.txt", "last", timestamp);
            writeEntry(output, "a.txt", "first", timestamp);
        }
    }

    private static void writeEntry(ZipOutputStream output, String name, String value, long timestamp) throws Exception {
        var entry = new ZipEntry(name);
        entry.setTime(timestamp);
        output.putNextEntry(entry);
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

}
