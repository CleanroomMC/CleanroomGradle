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

class PluginApplyTest {

    @TempDir
    Path projectDir;

    private PluginBuild project;

    @BeforeEach
    void setup() throws IOException {
        this.project = new PluginBuild(this.projectDir).settings();
    }

    @Test
    void appliesLazily() throws IOException {
        this.project.vanilla("");

        var quiet = this.project.runner("help", "-Pmc=missing-version-for-lazy-configuration", "--offline").build();
        assertEquals(TaskOutcome.SUCCESS, quiet.task(":help").getOutcome());
        assertFalse(quiet.getOutput().contains("Applying CleanroomGradle"));

        var info = this.project.runner("help", "--info").build();
        assertTrue(info.getOutput().contains("Applying CleanroomGradle"));
    }

    @Test
    void cleanroomInfoIsConfigurationCacheCompatible() throws IOException {
        this.project.vanilla("""
                cleanroom {
                    caches {
                        directory = layout.projectDirectory.dir('shared-cache')
                        localDirectory = layout.projectDirectory.dir('work-cache')
                    }
                }
                dependencies {
                    decompiler 'example:replacement-decompiler:1.0'
                }
                """);
        var clientJar = this.projectDir.resolve("shared-cache/versions/1.12.2/client.jar");
        Files.createDirectories(clientJar.getParent());
        Files.writeString(clientJar, "cached");

        var first = this.project.runner("cleanroomInfo", "--offline").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":cleanroomInfo").getOutcome());
        assertTrue(first.getOutput().contains("mode: vanilla"));
        assertTrue(first.getOutput().contains("Minecraft: 1.12.2"));
        assertTrue(first.getOutput().contains("shared cache: " + this.projectDir.resolve("shared-cache")));
        assertTrue(first.getOutput().contains("decompiler: example:replacement-decompiler:1.0"));
        assertTrue(first.getOutput().contains("client jar: ready"));
        assertTrue(first.getOutput().contains("server jar: missing"));

        PluginBuild.reused(this.project.runner("cleanroomInfo", "--offline").build().getOutput());
    }

    @Test
    void missingOfflineVersionMetadataHasRecovery() throws IOException {
        this.project.vanilla("""
                cleanroom {
                    caches.directory = layout.projectDirectory.dir('empty-cache')
                    minecraft.versionMetaUrl = 'https://example.invalid/version-meta.json'
                }
                """);

        var output = this.project.runner("cleanroomInfo", "--offline").buildAndFail().getOutput();
        assertTrue(output.contains("Gradle is offline and no cached version metadata exists at"));
        assertTrue(output.contains("https://example.invalid/version-meta.json"));
        assertTrue(output.contains("Run the requested task once without --offline"));
    }

}
