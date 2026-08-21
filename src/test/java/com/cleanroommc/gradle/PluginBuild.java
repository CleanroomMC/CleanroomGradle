package com.cleanroommc.gradle;

import org.gradle.testkit.runner.GradleRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PluginBuild {

    static final String PLUGIN_DIR = System.getProperty("plugin.project.dir", new File("").getAbsolutePath()).replace("\\", "/");

    final Path projectDir;

    PluginBuild(Path projectDir) {
        this.projectDir = projectDir;
    }

    PluginBuild settings() throws IOException {
        Files.writeString(this.projectDir.resolve("settings.gradle"), """
                pluginManagement {
                    includeBuild '%s'
                    repositories {
                        maven {
                            name = 'MinecraftForge'
                            url = 'https://maven.minecraftforge.net/'
                        }
                        gradlePluginPortal()
                    }
                }
                plugins {
                    id 'com.cleanroommc.cleanroomgradle.settings'
                }
                rootProject.name = 'test-project'
                """.formatted(PLUGIN_DIR));
        return this;
    }

    PluginBuild build(String body) throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }
                group = 'com.example'
                """ + body);
        return this;
    }

    PluginBuild vanilla(String extra) throws IOException {
        return build("""
                cleanroom {
                    mode = com.cleanroommc.gradle.api.ext.ProjectMode.VANILLA
                    patches.developInitial = false
                }
                """ + extra);
    }

    PluginBuild loader(String extra) throws IOException {
        return build("""
                cleanroom {
                    mode = com.cleanroommc.gradle.api.ext.ProjectMode.LOADER
                    patches.developInitial = false
                }
                """ + extra);
    }

    GradleRunner runner(String... args) {
        var allArgs = new ArrayList<>(Arrays.asList(args));
        allArgs.add("--console=plain");
        allArgs.add("--configuration-cache");
        allArgs.add("--configuration-cache-problems=fail");
        var runner = GradleRunner.create().withProjectDir(this.projectDir.toFile()).withArguments(allArgs);
        var testKitHome = System.getProperty("testkit.gradle.user.home");
        if (testKitHome != null) {
            runner.withTestKitDir(new File(testKitHome));
        }
        return runner;
    }

    void assertProblem(String problemId) throws IOException {
        var report = this.projectDir.resolve("build/reports/problems/problems-report.html");
        assertTrue(Files.isRegularFile(report), "Gradle Problems report was not generated");
        assertTrue(Files.readString(report).contains(problemId),
                "Problems report does not contain '" + problemId + "'");
    }

    static void scheduled(String output, String... tasks) {
        for (var task : tasks) {
            assertTrue(output.contains(":" + task), () -> "missing :" + task + " in\n" + output);
        }
    }

    static void notScheduled(String output, String... tasks) {
        for (var task : tasks) {
            assertFalse(output.contains(":" + task), () -> "unexpected :" + task + " in\n" + output);
        }
    }

    static void reused(String output) {
        assertTrue(output.contains("Reusing configuration cache"),
                () -> "configuration cache was not reused:\n" + output);
    }

}
