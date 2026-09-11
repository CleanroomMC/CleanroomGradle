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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Shared setup for functional tests: one isolated project directory plus the
 * settings file that includes this plugin build. Concrete tests only declare
 * their {@code build.gradle} body and the tasks to run.
 */
abstract class BaseFunctionalTest {

    @TempDir
    Path projectDir;

    PluginBuild project;

    @BeforeEach
    void setupBase() throws IOException {
        this.project = new PluginBuild(this.projectDir).settings();
    }

}
