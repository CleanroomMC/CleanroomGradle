package com.cleanroommc.gradle;

import org.apache.commons.codec.digest.DigestUtils;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftCacheTest {

    @TempDir
    Path projectDir;

    private PluginBuild project;

    @BeforeEach
    void setup() throws IOException {
        this.project = new PluginBuild(this.projectDir).settings();
    }

    @Test
    void cleanPreservesSharedCache() throws IOException {
        this.project.vanilla("""
                cleanroom {
                    caches {
                        directory.set(layout.projectDirectory.dir('cleanroom-cache'))
                        localDirectory.set(layout.projectDirectory.dir('local-cache'))
                    }
                }
                """);

        var cacheMarker = this.projectDir.resolve("cleanroom-cache/marker");
        var localCacheMarker = this.projectDir.resolve("local-cache/marker");
        Files.createDirectories(cacheMarker.getParent());
        Files.createDirectories(localCacheMarker.getParent());
        Files.createFile(cacheMarker);
        Files.createFile(localCacheMarker);

        assertEquals(TaskOutcome.SUCCESS, this.project.runner("clean").build().task(":clean").getOutcome());
        assertTrue(Files.exists(cacheMarker), "Ordinary clean deleted the shared CleanroomGradle cache");
        assertFalse(Files.exists(localCacheMarker.getParent()), "Local CleanroomGradle cache was not deleted");

        var sharedClean = this.project.runner("cleanCleanroomSharedCache").build();
        assertEquals(TaskOutcome.SUCCESS, sharedClean.task(":cleanCleanroomSharedCache").getOutcome());
        assertFalse(Files.exists(cacheMarker.getParent()), "Explicit shared-cache cleanup did not delete the cache");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void discardIntermediates(boolean discard) throws IOException {
        this.project.build("""
                import com.cleanroommc.gradle.api.task.IntermediateProcessor

                cleanroom {
                    mode = 'vanilla'
                    caches {
                        discardIntermediates = %s
                        localDirectory.set(layout.buildDirectory.dir('cleanroom_gradle'))
                    }
                }
                def mid = layout.buildDirectory.file('cleanroom_gradle/mid.txt')
                def writeMid = tasks.register('writeMid') {
                    outputs.file(mid)
                    doLast { mid.get().asFile.text = 'mid' }
                }
                def readMid = tasks.register('readMid') {
                    inputs.file(mid)
                    dependsOn writeMid
                    doLast { assert mid.get().asFile.file }
                }
                IntermediateProcessor.of(project).discardAfter(readMid, mid)
                """.formatted(discard));

        var result = this.project.runner("readMid").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":readMid").getOutcome());
        assertEquals(discard ? TaskOutcome.SUCCESS : TaskOutcome.SKIPPED,
                result.task(":discardReadMidIntermediates").getOutcome());
        var intermediate = this.projectDir.resolve("build/cleanroom_gradle/mid.txt");
        if (discard) {
            assertFalse(Files.exists(intermediate), "intermediate file was left behind");
        } else {
            assertEquals("mid", Files.readString(intermediate));
        }
    }

    @Test
    void sharedIntermediatesSurviveUntilEveryConsumerHasRun() throws IOException {
        this.project.build("""
                import com.cleanroommc.gradle.api.task.IntermediateProcessor

                cleanroom {
                    mode = 'vanilla'
                    caches {
                        discardIntermediates = true
                        localDirectory.set(layout.buildDirectory.dir('cleanroom_gradle'))
                    }
                }
                def shared = layout.buildDirectory.file('cleanroom_gradle/shared.txt')
                def writeShared = tasks.register('writeShared') {
                    outputs.file(shared)
                    doLast { shared.get().asFile.text = 'shared' }
                }
                def first = tasks.register('firstConsumer') {
                    dependsOn writeShared
                    doLast { assert shared.get().asFile.file }
                }
                def second = tasks.register('secondConsumer') {
                    dependsOn writeShared
                    mustRunAfter first
                    doLast { assert shared.get().asFile.file }
                }
                IntermediateProcessor.of(project).discardAfterAll([first, second], shared)
                """);

        var intermediate = this.projectDir.resolve("build/cleanroom_gradle/shared.txt");

        // A build that never touches the pipeline must not delete anything
        this.project.runner("help").build();
        assertFalse(Files.exists(intermediate));

        this.project.runner("writeShared").build();
        assertEquals("shared", Files.readString(intermediate));
        this.project.runner("help").build();
        assertEquals("shared", Files.readString(intermediate), "an untouched intermediate was deleted");

        var one = this.project.runner("firstConsumer").build();
        assertEquals(TaskOutcome.SUCCESS, one.task(":firstConsumer").getOutcome());
        assertNull(one.task(":secondConsumer"), "the other consumer was pulled into the graph");
        assertFalse(Files.exists(intermediate), "intermediate survived a requested consumer");

        this.project.runner("firstConsumer", "secondConsumer").build();
        assertFalse(Files.exists(intermediate), "intermediate survived its last consumer");
    }

    @Test
    void upToDateConsumerStillDiscards() throws IOException {
        this.project.build("""
                import com.cleanroommc.gradle.api.task.IntermediateProcessor

                cleanroom {
                    mode = 'vanilla'
                    caches {
                        discardIntermediates = providers.gradleProperty('discardIntermediates')
                                .map { it.toBoolean() }
                                .orElse(false)
                        localDirectory.set(layout.buildDirectory.dir('cleanroom_gradle'))
                    }
                }
                def mid = layout.buildDirectory.file('cleanroom_gradle/mid.txt')
                def out = layout.buildDirectory.file('out.txt')
                def writeMid = tasks.register('writeMid') {
                    outputs.file(mid)
                    doLast { mid.get().asFile.text = 'mid' }
                }
                def readMid = tasks.register('readMid') {
                    inputs.file(mid)
                    outputs.file(out)
                    dependsOn writeMid
                    doLast {
                        assert mid.get().asFile.file
                        out.get().asFile.text = 'ok'
                    }
                }
                IntermediateProcessor.of(project).discardAfter(readMid, mid)
                """);

        var intermediate = this.projectDir.resolve("build/cleanroom_gradle/mid.txt");
        var first = this.project.runner("readMid", "-PdiscardIntermediates=false").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":readMid").getOutcome());
        assertEquals("mid", Files.readString(intermediate));

        var second = this.project.runner("readMid", "-PdiscardIntermediates=true").build();
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":writeMid").getOutcome());
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":readMid").getOutcome());
        assertFalse(Files.exists(intermediate), "up-to-date consumer left the intermediate behind");
    }

    @Test
    void discardIntermediatesIsDecidedPerProject() throws IOException {
        this.project.vanilla("");
        Files.writeString(this.projectDir.resolve("settings.gradle"), "\ninclude 'keep', 'drop'\n",
                StandardOpenOption.APPEND);
        subproject("keep", false);
        subproject("drop", true);

        var result = this.project.runner(":keep:readMid", ":drop:readMid").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":keep:readMid").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":drop:readMid").getOutcome());
        assertEquals("mid", Files.readString(this.projectDir.resolve("keep/build/cleanroom_gradle/mid.txt")),
                "a project that keeps its intermediates followed another project's setting");
        assertFalse(Files.exists(this.projectDir.resolve("drop/build/cleanroom_gradle/mid.txt")),
                "a project that discards its intermediates followed another project's setting");
    }

    private void subproject(String name, boolean discard) throws IOException {
        var directory = this.projectDir.resolve(name);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }
                import com.cleanroommc.gradle.api.task.IntermediateProcessor

                cleanroom {
                    mode = 'vanilla'
                    caches {
                        discardIntermediates = %s
                        localDirectory.set(layout.buildDirectory.dir('cleanroom_gradle'))
                    }
                }
                def mid = layout.buildDirectory.file('cleanroom_gradle/mid.txt')
                def writeMid = tasks.register('writeMid') {
                    outputs.file(mid)
                    doLast { mid.get().asFile.text = 'mid' }
                }
                def readMid = tasks.register('readMid') {
                    inputs.file(mid)
                    dependsOn writeMid
                    doLast { assert mid.get().asFile.file }
                }
                IntermediateProcessor.of(project).discardAfter(readMid, mid)
                """.formatted(discard));
    }

    @Test
    void downloadsSkipWhenCacheMatches() throws IOException {
        var clientBytes = "cached-client".getBytes(StandardCharsets.UTF_8);
        var serverBytes = "cached-server".getBytes(StandardCharsets.UTF_8);
        var indexBytes = "{\"objects\":{}}".getBytes(StandardCharsets.UTF_8);
        var cache = this.projectDir.resolve("cg-cache");
        this.project.seedLauncherMeta(cache, "1.12.2",
                """
                        {
                          "assetIndex": {
                            "id": "1.12",
                            "sha1": "%s",
                            "size": %d,
                            "url": "https://example.invalid/1.12.json"
                          },
                          "downloads": {
                            "client": { "sha1": "%s", "size": %d, "url": "https://example.invalid/client.jar" },
                            "server": { "sha1": "%s", "size": %d, "url": "https://example.invalid/server.jar" }
                          },
                          "id": "1.12.2"
                        }
                        """.formatted(DigestUtils.sha1Hex(indexBytes), indexBytes.length,
                        DigestUtils.sha1Hex(clientBytes), clientBytes.length,
                        DigestUtils.sha1Hex(serverBytes), serverBytes.length));
        this.project.build("""
                import de.undercouch.gradle.tasks.download.Download

                cleanroom {
                    mode = 'vanilla'
                    caches.directory.set(layout.projectDirectory.dir('cg-cache'))
                }
                // gradle-download-task logs via Task.project when it skips existing files in --offline.
                tasks.withType(Download).configureEach { quiet(true) }
                """);

        var versionCache = cache.resolve("versions/1.12.2");
        var indexCache = cache.resolve("assets/indexes");
        Files.createDirectories(indexCache);
        Files.write(versionCache.resolve("client.jar"), clientBytes);
        Files.write(versionCache.resolve("server.jar"), serverBytes);
        Files.write(indexCache.resolve("1.12.json"), indexBytes);

        var cached = this.project.runner("downloadClientJar", "downloadServerJar", "downloadAssetIndex", "--offline").build();
        assertEquals(TaskOutcome.SKIPPED, cached.task(":downloadClientJar").getOutcome());
        assertEquals(TaskOutcome.SKIPPED, cached.task(":downloadServerJar").getOutcome());
        assertEquals(TaskOutcome.SKIPPED, cached.task(":downloadAssetIndex").getOutcome());
    }

    @Test
    void assetOutputsAreIndexScoped() throws IOException {
        var assetBytes = "cached-asset".getBytes(StandardCharsets.UTF_8);
        var assetSha1 = DigestUtils.sha1Hex(assetBytes);
        var assetPath = "objects/" + assetSha1.substring(0, 2) + "/" + assetSha1;
        Files.writeString(this.projectDir.resolve("asset-index.json"), """
                {"objects":{"example":{"hash":"%s","size":%d}}}
                """.formatted(assetSha1, assetBytes.length));
        var cachedAsset = this.projectDir.resolve(assetPath);
        Files.createDirectories(cachedAsset.getParent());
        Files.write(cachedAsset, assetBytes);

        this.project.vanilla("""
                def expected = file('%s')
                gradle.projectsEvaluated {
                    tasks.named('downloadAssets') {
                        assetIndexFile = layout.projectDirectory.file('asset-index.json')
                        objects = layout.projectDirectory.dir('objects')
                        doFirst { task ->
                            logger.lifecycle('ASSET_TASK_EXECUTED')
                            assert task.outputs.files.files == [expected] as Set
                        }
                    }
                }
                """.formatted(assetPath));

        assertTrue(this.project.runner("downloadAssets").build().getOutput().contains("ASSET_TASK_EXECUTED"));

        Files.writeString(this.projectDir.resolve("objects/unrelated-object"), "other index");
        var second = this.project.runner("downloadAssets").build();
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":downloadAssets").getOutcome());
        assertFalse(second.getOutput().contains("ASSET_TASK_EXECUTED"));
    }

    @Test
    void offlineAssetValidationReportsProblems() throws IOException {
        var missingHash = "1111111111111111111111111111111111111111";
        var corruptHash = "2222222222222222222222222222222222222222";
        Files.writeString(this.projectDir.resolve("asset-index.json"), """
                {
                  "objects": {
                    "minecraft/sounds/missing.ogg": {"hash":"%s","size":4},
                    "minecraft/textures/corrupt.png": {"hash":"%s","size":7}
                  }
                }
                """.formatted(missingHash, corruptHash));
        var corruptAsset = this.projectDir.resolve("objects/22/" + corruptHash);
        Files.createDirectories(corruptAsset.getParent());
        Files.writeString(corruptAsset, "corrupt");
        this.project.vanilla("""
                gradle.projectsEvaluated {
                    tasks.named('downloadAssets') {
                        assetIndexFile = layout.projectDirectory.file('asset-index.json')
                        objects = layout.projectDirectory.dir('objects')
                    }
                }
                """);

        var result = this.project.runner("downloadAssets", "--offline").buildAndFail();
        assertTrue(result.getOutput().contains("2 Minecraft asset(s) are missing or invalid"));
        assertTrue(result.getOutput().contains("minecraft/sounds/missing.ogg: object is missing"));
        assertTrue(result.getOutput().contains("minecraft/textures/corrupt.png: SHA-1 does not match"));
        assertTrue(result.getOutput().contains("Run downloadAssets once without --offline to repair the shared asset cache"));
        this.project.assertProblem("offline-assets");
    }

}
