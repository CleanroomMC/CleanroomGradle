package com.cleanroommc.gradle;

import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BinPatchTest {

    @TempDir
    Path projectDir;

    private PluginBuild project;

    @BeforeEach
    void setup() throws IOException {
        this.project = new PluginBuild(this.projectDir).settings();
    }

    @Test
    void roundTripPreservesJarContents() throws IOException {
        var original = this.projectDir.resolve("original.jar");
        var modified = this.projectDir.resolve("modified.jar");
        writeArchive(original, List.of(
                new ArchiveEntry("a/A.class", "old"),
                new ArchiveEntry("b/B.class", "removed"),
                new ArchiveEntry("resource.txt", "resource")));
        writeArchive(modified, List.of(
                new ArchiveEntry("a/A.class", "new class contents"),
                new ArchiveEntry("c/C.class", "added")));

        this.project.vanilla("""
                import com.cleanroommc.gradle.api.task.patch.ApplyBinPatches
                import com.cleanroommc.gradle.api.task.patch.GenerateBinPatches

                def patches = layout.buildDirectory.file('test.binpatches')
                def generate = tasks.register('generateTestBinPatches', GenerateBinPatches) {
                    originalJar = layout.projectDirectory.file('original.jar')
                    modifiedJar = layout.projectDirectory.file('modified.jar')
                    includedPrefixes = []
                    binpatches = patches
                }
                tasks.register('applyTestBinPatches', ApplyBinPatches) {
                    dependsOn generate
                    originalJar = layout.projectDirectory.file('original.jar')
                    binpatches = patches
                    patchedJar = layout.buildDirectory.file('patched.jar')
                }
                """);

        var result = this.project.runner("applyTestBinPatches").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateTestBinPatches").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":applyTestBinPatches").getOutcome());
        try (var zip = new ZipFile(this.projectDir.resolve("build/patched.jar").toFile())) {
            assertEquals("new class contents", readEntry(zip, "a/A.class"));
            assertNull(zip.getEntry("b/B.class"));
            assertEquals("added", readEntry(zip, "c/C.class"));
            assertEquals("resource", readEntry(zip, "resource.txt"));
        }
    }

    @Test
    void splitKeepsAddedMinecraftClassesInSlimJar() throws IOException {
        writeArchive(this.projectDir.resolve("patched.jar"), List.of(
                new ArchiveEntry("ain.class", "mapped"),
                new ArchiveEntry("ain$22.class", "added inner"),
                new ArchiveEntry("net/minecraft/NewClass.class", "added class"),
                new ArchiveEntry("library/Helper.class", "library")));
        Files.writeString(this.projectDir.resolve("joined.tsrg"), "ain net/minecraft/MappedClass\n");

        this.project.vanilla("""
                import com.cleanroommc.gradle.api.task.mcp.SplitJar

                tasks.register('splitPatchedJar', SplitJar) {
                    sourceJar = layout.projectDirectory.file('patched.jar')
                    srgMappingFile = layout.projectDirectory.file('joined.tsrg')
                    slimJar = layout.buildDirectory.file('slim.jar')
                    extraJar = layout.buildDirectory.file('extra.jar')
                }
                """);

        var result = this.project.runner("splitPatchedJar").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":splitPatchedJar").getOutcome());
        try (var slim = new ZipFile(this.projectDir.resolve("build/slim.jar").toFile());
             var extra = new ZipFile(this.projectDir.resolve("build/extra.jar").toFile())) {
            assertNotNull(slim.getEntry("ain.class"));
            assertNotNull(slim.getEntry("ain$22.class"));
            assertNotNull(slim.getEntry("net/minecraft/NewClass.class"));
            assertNull(slim.getEntry("library/Helper.class"));
            assertNull(extra.getEntry("ain.class"));
            assertNull(extra.getEntry("ain$22.class"));
            assertNull(extra.getEntry("net/minecraft/NewClass.class"));
            assertNotNull(extra.getEntry("library/Helper.class"));
        }
    }

    private static void writeArchive(Path output, List<ArchiveEntry> entries) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(output))) {
            for (var entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.contents().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static String readEntry(ZipFile zip, String name) throws IOException {
        try (var input = zip.getInputStream(zip.getEntry(name))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record ArchiveEntry(String name, String contents) { }

}
