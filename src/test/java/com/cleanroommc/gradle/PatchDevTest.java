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

import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class PatchDevTest extends BaseFunctionalTest {

    @Test
    void generateApplyAndPreserveDirtyEdits() throws IOException {
        Files.createDirectories(this.projectDir.resolve("build/input-src"));
        Files.writeString(this.projectDir.resolve("build/input-src/A.java"), "class A {}\n");
        this.project.build(
                """
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
                """
        );

        var first = this.project.runner("prepareExamplePatchDevEnvironment").build();
        assertThat(first.task(":prepareExamplePatchDevEnvironment").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(first.getOutput()).contains("Configuration cache entry stored");
        var output = this.projectDir.resolve("custom-output/A.java");
        assertThat(Files.exists(output)).as("configured patchDev output was not populated").isTrue();
        assertThat(Files.isDirectory(this.projectDir.resolve("custom-patches"))).isTrue();

        Files.writeString(output, "class A { int value; }\n");
        assertThat(this.project.runner("generateExampleDiffs").build().task(":generateExampleDiffs").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.exists(this.projectDir.resolve("custom-patches/A.java.patch"))).isTrue();

        Files.writeString(output, "class A { int stale; }\n");
        Files.writeString(output.getParent().resolve("Stale.java"), "class Stale {}\n");
        var applied = this.project.runner("applyExampleDiffs").build();
        assertThat(applied.task(":applyExampleDiffs").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.readString(output)).isEqualTo("class A { int value; }\n");
        assertThat(Files.exists(output.getParent().resolve("Stale.java"))).isFalse();
        var dirty = this.projectDir.resolve("build/cleanroom_gradle/patchDev/example/dirty");
        assertThat(Files.readString(dirty.resolve("A.java"))).isEqualTo("class A { int stale; }\n");
        assertThat(Files.readString(dirty.resolve("Stale.java"))).isEqualTo("class Stale {}\n");
        assertThat(applied.getOutput()).contains("did not match the current patch set");

        Files.writeString(output, "class A { int value; int working; }\n");
        var second = this.project.runner("prepareExamplePatchDevEnvironment").build();
        assertThat(second.task(":prepareExamplePatchDevEnvironment").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        PluginBuild.reused(second.getOutput());
        assertThat(Files.readString(output))
                .as("preparing the environment overwrote edits in the populated development source tree")
                .isEqualTo("class A { int value; int working; }\n");
    }

}
