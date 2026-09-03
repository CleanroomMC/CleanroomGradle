package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.util.IO;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.model.idea.IdeaProject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
                            name = 'CleanroomMC'
                            url = 'https://maven.cleanroommc.com/'
                        }
                        mavenCentral()
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
                java.toolchain.languageVersion = JavaLanguageVersion.of(25)
                """ + body);
        return this;
    }

    PluginBuild vanilla(String extra) throws IOException {
        return build("""
                cleanroom {
                    mode = 'vanilla'
                    patches.developInitial = false
                }
                """ + extra);
    }

    void seedLauncherMeta(Path cacheDirectory, String version, String metaJson) throws IOException {
        var metaFile = cacheDirectory.resolve("versions").resolve(version).resolve("meta.json");
        Files.createDirectories(metaFile.getParent());
        Files.writeString(metaFile, metaJson);
        Files.writeString(cacheDirectory.resolve("version_manifest_v2.json"),
                """
                        {"versions":[{"id":"%s","url":"https://example.invalid/%s.json","sha1":"%s"}]}
                        """.formatted(version, version, IO.sha1(metaFile)));
    }

    PluginBuild loader(String extra) throws IOException {
        return build("""
                cleanroom {
                    mode = 'loader'
                    patches.developInitial = false
                }
                """ + extra);
    }

    /**
     * A userdev artifact carrying just enough metadata for a workspace to configure against. The pipeline
     * inputs a workspace resolves come out of this file, so any test in userdev mode needs one.
     */
    static void writeUserdevJar(Path jar, String version) throws IOException {
        UserdevFixture.writeArtifact(jar, version, new UserdevFixture.Spec(), "client", "server");
    }

    /** The spec 1 document the artifact carries, which every consumer reads its layout from. */
    static String userdevConfigJson(String version) {
        return UserdevFixture.config(version, "client", "server");
    }

    void seedUserdevModule(String version) throws IOException {
        UserdevFixture.seed(this.projectDir, version);
    }

    /**
     * Serves the artifact as a module, for the paths that resolve it by coordinate instead of by file.
     * {@code com.cleanroommc} is bound to the Cleanroom maven with {@code exclusiveContent}, so a repository
     * declared by the buildscript is never consulted for it: the local maven the plugin can be told to add
     * to that same exclusive content is the only way in. Returns the arguments that switch it on.
     */
    String[] userdevModuleArgs(String version, String... args) throws IOException {
        seedUserdevModule(version);
        var all = new ArrayList<>(Arrays.asList(args));
        all.add("-Pcg.repos.enableLocal=true");
        all.add("-Dmaven.repo.local=" + this.projectDir.resolve("local-maven"));
        return all.toArray(String[]::new);
    }

    GradleRunner runner(String... args) {
        var allArgs = new ArrayList<>(Arrays.asList(args));
        allArgs.add("--configuration-cache");
        allArgs.add("--configuration-cache-problems=fail");
        return plainRunner(allArgs.toArray(String[]::new));
    }

    /**
     * A runner without the configuration cache, for a build Gradle itself cannot cache.
     */
    GradleRunner plainRunner(String... args) {
        var allArgs = new ArrayList<>(Arrays.asList(args));
        allArgs.add("--console=plain");
        var runner = GradleRunner.create().withProjectDir(this.projectDir.toFile()).withArguments(allArgs);
        var testKitHome = System.getProperty("testkit.gradle.user.home");
        if (testKitHome != null) {
            runner.withTestKitDir(new File(testKitHome));
        }
        return runner;
    }

    IdeaModel ideaModel(String... args) {
        var allArgs = new ArrayList<>(Arrays.asList(args));
        allArgs.add("--configuration-cache");
        allArgs.add("--configuration-cache-problems=fail");
        var output = new ByteArrayOutputStream();
        try (var connection = GradleConnector.newConnector()
                .useInstallation(new File(System.getProperty("test.gradle.home")))
                .useGradleUserHomeDir(new File(System.getProperty("testkit.gradle.user.home")))
                .forProjectDirectory(this.projectDir.toFile())
                .connect()) {
            var model = connection.model(IdeaProject.class)
                    .withArguments(allArgs.toArray(String[]::new))
                    .setStandardOutput(output)
                    .setStandardError(output)
                    .get();
            return new IdeaModel(model, output.toString(StandardCharsets.UTF_8));
        }
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

    record IdeaModel(IdeaProject value, String output) { }

}
