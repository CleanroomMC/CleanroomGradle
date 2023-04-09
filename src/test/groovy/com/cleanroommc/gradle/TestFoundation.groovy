package com.cleanroommc.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach

import java.nio.file.Files
import java.nio.file.Path

abstract class TestFoundation {

    Path userHomeDir = Files.createTempDirectory('gradleUserHome')
    Path projectDir = Files.createTempDirectory('projectDirectory')

    @BeforeEach
    void beforeEach() {
        Path buildFile = projectDir.resolve('build.gradle')
        buildFile <<
                """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroom-gradle'
                }
                """
        appendBuildScript(buildFile)
    }

    @AfterEach
    void afterEach() {
        userHomeDir.toFile().deleteDir()
        projectDir.toFile().deleteDir()
    }

    abstract void appendBuildScript(Path buildFile)

    GradleRunner gradleRunner() {
        return GradleRunner.create()
                .withTestKitDir(userHomeDir.toFile())
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .forwardOutput()
    }

    boolean isSuccessful(String taskName, BuildResult buildResult) {
        return isSuccessful(buildResult.task(":${taskName}"))
    }

    boolean isSuccessful(BuildTask buildTask) {
        return Assertions.assertEquals(TaskOutcome.SUCCESS, buildTask.outcome)
    }

    boolean isUpToDate(String taskName, BuildResult buildResult) {
        return isSuccessful(buildResult.task(":${taskName}"))
    }

    boolean isUpToDate(BuildTask buildTask) {
        return Assertions.assertEquals(TaskOutcome.UP_TO_DATE, buildTask.outcome)
    }

}
