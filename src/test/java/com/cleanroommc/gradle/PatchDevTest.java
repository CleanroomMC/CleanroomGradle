package com.cleanroommc.gradle;

import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchDevTest {

    @TempDir
    Path projectDir;

    private PluginBuild project;

    @BeforeEach
    void setup() throws IOException {
        this.project = new PluginBuild(this.projectDir).settings();
    }

    @Test
    void generateApplyAndPreserveDirtyEdits() throws IOException {
        Files.createDirectories(this.projectDir.resolve("build/input-src"));
        Files.writeString(this.projectDir.resolve("build/input-src/A.java"), "class A {}\n");
        this.project.build("""
                cleanroom {
                    mode = 'vanilla'
                    patches {
                        patchDev {
                            example {
                                input = layout.buildDirectory.dir('input-src')
                                patches = layout.projectDirectory.dir('custom-patches')
                                output = layout.projectDirectory.dir('custom-output')
                            }
                        }
                    }
                }
                tasks.named('prepareExamplePatchDevEnvironment') {
                    description = 'configured before project evaluation completes'
                }
                """);

        var first = this.project.runner("prepareExamplePatchDevEnvironment").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":prepareExamplePatchDevEnvironment").getOutcome());
        assertTrue(first.getOutput().contains("Configuration cache entry stored"));
        var output = this.projectDir.resolve("custom-output/A.java");
        assertTrue(Files.exists(output), "configured patchDev output was not populated");
        assertTrue(Files.isDirectory(this.projectDir.resolve("custom-patches")));

        Files.writeString(output, "class A { int value; }\n");
        assertEquals(TaskOutcome.SUCCESS, this.project.runner("generateExampleDiffs").build()
                .task(":generateExampleDiffs").getOutcome());
        assertTrue(Files.exists(this.projectDir.resolve("custom-patches/A.java.patch")));

        Files.writeString(output, "class A { int stale; }\n");
        Files.writeString(output.getParent().resolve("Stale.java"), "class Stale {}\n");
        var applied = this.project.runner("applyExampleDiffs").build();
        assertEquals(TaskOutcome.SUCCESS, applied.task(":applyExampleDiffs").getOutcome());
        assertEquals("class A { int value; }\n", Files.readString(output));
        assertFalse(Files.exists(output.getParent().resolve("Stale.java")));
        var dirty = this.projectDir.resolve("build/cleanroom_gradle/patchDev/example/dirty");
        assertEquals("class A { int stale; }\n", Files.readString(dirty.resolve("A.java")));
        assertEquals("class Stale {}\n", Files.readString(dirty.resolve("Stale.java")));
        assertTrue(applied.getOutput().contains("did not match the current patch set"));

        Files.writeString(output, "class A { int value; int working; }\n");
        var second = this.project.runner("prepareExamplePatchDevEnvironment").build();
        assertEquals(TaskOutcome.SUCCESS, second.task(":prepareExamplePatchDevEnvironment").getOutcome());
        PluginBuild.reused(second.getOutput());
        assertEquals("class A { int value; int working; }\n", Files.readString(output),
                "preparing the environment overwrote edits in the populated development source tree");
    }

}
