/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle;

import org.junit.jupiter.api.Test;

import org.gradle.testkit.runner.TaskOutcome;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class BinPatchTest extends BaseFunctionalTest {

    @Test
    void roundTripPreservesJarContents() throws IOException {
        var original = this.projectDir.resolve("original.jar");
        var modified = this.projectDir.resolve("modified.jar");
        writeArchive(
                original,
                List.of(new ArchiveEntry("a/A.class", "old"), new ArchiveEntry("b/B.class", "removed"), new ArchiveEntry("resource.txt", "resource"))
        );
        writeArchive(modified, List.of(new ArchiveEntry("a/A.class", "new class contents"), new ArchiveEntry("c/C.class", "added")));
        var serverModified = this.projectDir.resolve("server-modified.jar");
        writeArchive(serverModified, List.of(new ArchiveEntry("a/A.class", "new server class contents"), new ArchiveEntry("b/B.class", "removed")));

        this.project.vanilla(
                """
                import com.cleanroommc.gradle.api.task.patch.ApplyBinPatches
                import com.cleanroommc.gradle.api.task.patch.GenerateBinPatches

                def patches = layout.buildDirectory.file('test.binpatches')
                def generate = tasks.register('generateTestBinPatches', GenerateBinPatches) {
                    clientOriginalJar = layout.projectDirectory.file('original.jar')
                    clientModifiedJar = layout.projectDirectory.file('modified.jar')
                    clientPrefix = 'binpatch/client/'
                    serverOriginalJar = layout.projectDirectory.file('original.jar')
                    serverModifiedJar = layout.projectDirectory.file('server-modified.jar')
                    serverPrefix = 'binpatch/server/'
                    includedPrefixes = []
                    binpatches = patches
                }
                tasks.register('applyTestBinPatches', ApplyBinPatches) {
                    dependsOn generate
                    originalJar = layout.projectDirectory.file('original.jar')
                    binpatches = patches
                    prefix = 'binpatch/client/'
                    patchedJar = layout.buildDirectory.file('patched.jar')
                }
                tasks.register('applyTestServerBinPatches', ApplyBinPatches) {
                    dependsOn generate
                    originalJar = layout.projectDirectory.file('original.jar')
                    binpatches = patches
                    prefix = 'binpatch/server/'
                    patchedJar = layout.buildDirectory.file('server-patched.jar')
                }
                """
        );

        var result = this.project.runner("applyTestBinPatches", "applyTestServerBinPatches").build();
        assertThat(result.task(":generateTestBinPatches").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":applyTestBinPatches").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        try (var zip = new ZipFile(this.projectDir.resolve("build/patched.jar").toFile())) {
            assertThat(readEntry(zip, "a/A.class")).isEqualTo("new class contents");
            assertThat(zip.getEntry("b/B.class")).isNull();
            assertThat(readEntry(zip, "c/C.class")).isEqualTo("added");
            assertThat(readEntry(zip, "resource.txt")).isEqualTo("resource");
        }
        // The server side of the same archive has to round-trip independently of the client side
        try (var zip = new ZipFile(this.projectDir.resolve("build/server-patched.jar").toFile())) {
            assertThat(readEntry(zip, "a/A.class")).isEqualTo("new server class contents");
            assertThat(readEntry(zip, "b/B.class")).isEqualTo("removed");
            assertThat(zip.getEntry("c/C.class")).isNull();
        }
    }

    @Test
    void splitKeepsAddedMinecraftClassesInSlimJar() throws IOException {
        writeArchive(
                this.projectDir.resolve("patched.jar"),
                List.of(
                        new ArchiveEntry("ain.class", "mapped"),
                        new ArchiveEntry("ain$22.class", "added inner"),
                        new ArchiveEntry("net/minecraft/NewClass.class", "added class"),
                        new ArchiveEntry("library/Helper.class", "library")
                )
        );
        Files.writeString(this.projectDir.resolve("joined.tsrg"), "ain net/minecraft/MappedClass\n");

        this.project.vanilla(
                """
                import com.cleanroommc.gradle.api.task.mcp.SplitJar

                tasks.register('splitPatchedJar', SplitJar) {
                    sourceJar = layout.projectDirectory.file('patched.jar')
                    srgMappingFile = layout.projectDirectory.file('joined.tsrg')
                    slimJar = layout.buildDirectory.file('slim.jar')
                    extraJar = layout.buildDirectory.file('extra.jar')
                }
                """
        );

        var result = this.project.runner("splitPatchedJar").build();
        assertThat(result.task(":splitPatchedJar").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        try (var slim = new ZipFile(this.projectDir.resolve("build/slim.jar").toFile()); var extra = new ZipFile(
                this.projectDir.resolve("build/extra.jar").toFile()
        )) {
            assertThat(slim.getEntry("ain.class")).isNotNull();
            assertThat(slim.getEntry("ain$22.class")).isNotNull();
            assertThat(slim.getEntry("net/minecraft/NewClass.class")).isNotNull();
            assertThat(slim.getEntry("library/Helper.class")).isNull();
            assertThat(extra.getEntry("ain.class")).isNull();
            assertThat(extra.getEntry("ain$22.class")).isNull();
            assertThat(extra.getEntry("net/minecraft/NewClass.class")).isNull();
            assertThat(extra.getEntry("library/Helper.class")).isNotNull();
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
