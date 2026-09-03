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
