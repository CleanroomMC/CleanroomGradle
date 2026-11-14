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

import com.cleanroommc.gradle.api.task.common.WriteBootList;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class BootListResourceTest extends BaseFunctionalTest {

    @Test
    void theBootListIsAResource() throws IOException {
        this.project.loader(
            """
            // Neither Minecraft nor the loader gets built
            tasks.named('writeBootList') { classes = layout.projectDirectory.dir('classes') }
            """
        );

        this.project.runner("processResources").build();

        assertThat(this.projectDir.resolve("build/resources/main").resolve(WriteBootList.PATH)).isRegularFile();
    }

    @Test
    void assigningTheResourceDirectoriesFails() throws IOException {
        Files.createDirectories(this.projectDir.resolve("src/main/resources"));
        Files.writeString(this.projectDir.resolve("src/main/resources/log4j2.xml"), "");
        this.project.loader(
            """
            tasks.named('writeBootList') { classes = layout.projectDirectory.dir('classes') }
            sourceSets.main.resources.srcDirs = ['src/main/resources']
            """
        );

        var output = this.project.runner("processResources").buildAndFail().getOutput();

        assertThat(output).contains(WriteBootList.PATH + " is missing from the resources");
    }

}
