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
import java.util.function.Consumer

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
                
                // used to print the task graph
                gradle.taskGraph.whenReady {taskGraph ->
                    println "Tasks:"
                    taskGraph.getAllTasks().eachWithIndex{ task, n ->
                        println "" + (n + 1) + " " + task
                        task.dependsOn.eachWithIndex{ depObj, m ->
                        println "  " + (m + 1) + " DependsOn: " + depObj
                        }
                    }
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

    void createTest(Consumer<IGradleRunnerBuilder> runnerConfigure) {
        def runner = new GradleRunnerBuilder()
        runnerConfigure.accept(runner)
        runner.runBuild()
    }

    GradleRunnerBuilder createTest() {
        return new GradleRunnerBuilder()
    }

    class GradleRunnerBuilder implements IGradleRunnerBuilder {
        private GradleRunner runner
        private String taskName
        private Consumer<Tests> test

        private GradleRunnerBuilder() {
            runner = GradleRunner.create()
                    .withTestKitDir(userHomeDir.toFile())
                    .withProjectDir(projectDir.toFile())
                    .withPluginClasspath()
                    .forwardOutput()
                    .withDebug(true)
        }

        @Override
        void setTaskName(String taskName) {
            this.taskName = taskName
        }

        @Override
        void configureTests(Consumer<Tests> test) {
            this.test = test
        }

        void runBuild() {
            if (taskName == null || test == null) {
                throw new IllegalAccessException("Tests are not configured")
            }

            BuildResult buildResult = runner.build()
            Tests tests = new Tests(runner, taskName, buildResult)
            test.accept(tests)
        }

    }

    interface IGradleRunnerBuilder {
        void setTaskName(String taskName)

        void configureTests(Consumer<Tests> test)
    }

    class Tests {
        private GradleRunner runner
        private String taskName
        private BuildResult buildResult
        private BuildTask buildTask

        Tests(GradleRunner runner, String taskName, BuildResult buildResult) {
            this.runner = runner
            this.taskName = taskName
            this.buildResult = buildResult
            this.buildTask = buildResult.task(":${taskName}")
        }

        void isSuccessful(String taskName) {
            isInnerSuccessful(buildResult.task(":${taskName}"))
        }

        void isSuccessful() {
            isInnerSuccessful(this.buildTask)
        }

        private void isInnerSuccessful(BuildTask buildTask) {
            Assertions.assertEquals(TaskOutcome.SUCCESS, buildTask.outcome)
        }

        void isUpToDate(String taskName) {
            isInnerUpToDate(buildResult.task(":${taskName}"))
        }

        void isUpToDate() {
            isInnerUpToDate(this.buildTask)
        }

        private void isInnerUpToDate(BuildTask buildTask) {
            Assertions.assertEquals(TaskOutcome.UP_TO_DATE, buildTask.outcome)
        }

    }
}
