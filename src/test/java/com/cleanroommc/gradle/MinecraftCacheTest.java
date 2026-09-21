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

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.gradle.testkit.runner.TaskOutcome;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class MinecraftCacheTest extends BaseFunctionalTest {

    @Test
    void cleanPreservesSharedCache() throws IOException {
        this.project.vanilla(
                """
                cleanroom {
                    caches {
                        directory.set(layout.projectDirectory.dir('cleanroom-cache'))
                        localDirectory.set(layout.projectDirectory.dir('local-cache'))
                    }
                }
                """
        );

        var cacheMarker = this.projectDir.resolve("cleanroom-cache/marker");
        var localCacheMarker = this.projectDir.resolve("local-cache/marker");
        Files.createDirectories(cacheMarker.getParent());
        Files.createDirectories(localCacheMarker.getParent());
        Files.createFile(cacheMarker);
        Files.createFile(localCacheMarker);

        assertThat(this.project.runner("clean").build().task(":clean").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.exists(cacheMarker)).as("Ordinary clean deleted the shared CleanroomGradle cache").isTrue();
        assertThat(Files.exists(localCacheMarker.getParent())).as("Local CleanroomGradle cache was not deleted").isFalse();

        var sharedClean = this.project.runner("cleanCleanroomSharedCache").build();
        assertThat(sharedClean.task(":cleanCleanroomSharedCache").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.exists(cacheMarker.getParent())).as("Explicit shared-cache cleanup did not delete the cache").isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void discardIntermediates(boolean discard) throws IOException {
        this.project.build(
                """
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
                """.formatted(
                        discard
                )
        );

        var result = this.project.runner("readMid").build();
        assertThat(result.task(":readMid").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":discardReadMidIntermediates").getOutcome()).isEqualTo(discard ? TaskOutcome.SUCCESS : TaskOutcome.SKIPPED);
        var intermediate = this.projectDir.resolve("build/cleanroom_gradle/mid.txt");
        if (discard) {
            assertThat(Files.exists(intermediate)).as("intermediate file was left behind").isFalse();
        } else {
            assertThat(Files.readString(intermediate)).isEqualTo("mid");
        }
    }

    @Test
    void sharedIntermediatesSurviveUntilEveryConsumerHasRun() throws IOException {
        this.project.build(
                """
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
                """
        );

        var intermediate = this.projectDir.resolve("build/cleanroom_gradle/shared.txt");

        // A build that never touches the pipeline must not delete anything
        this.project.runner("help").build();
        assertThat(Files.exists(intermediate)).isFalse();

        this.project.runner("writeShared").build();
        assertThat(Files.readString(intermediate)).isEqualTo("shared");
        this.project.runner("help").build();
        assertThat(Files.readString(intermediate)).as("an untouched intermediate was deleted").isEqualTo("shared");

        var one = this.project.runner("firstConsumer").build();
        assertThat(one.task(":firstConsumer").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(one.task(":secondConsumer")).as("the other consumer was pulled into the graph").isNull();
        assertThat(Files.exists(intermediate)).as("intermediate survived a requested consumer").isFalse();

        this.project.runner("firstConsumer", "secondConsumer").build();
        assertThat(Files.exists(intermediate)).as("intermediate survived its last consumer").isFalse();
    }

    @Test
    void upToDateConsumerStillDiscards() throws IOException {
        this.project.build(
                """
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
                """
        );

        var intermediate = this.projectDir.resolve("build/cleanroom_gradle/mid.txt");
        var first = this.project.runner("readMid", "-PdiscardIntermediates=false").build();
        assertThat(first.task(":readMid").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(Files.readString(intermediate)).isEqualTo("mid");

        var second = this.project.runner("readMid", "-PdiscardIntermediates=true").build();
        assertThat(second.task(":writeMid").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
        assertThat(second.task(":readMid").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
        assertThat(Files.exists(intermediate)).as("up-to-date consumer left the intermediate behind").isFalse();
    }

    @Test
    void downloadsSkipWhenCacheMatches() throws IOException {
        var clientBytes = "cached-client".getBytes(StandardCharsets.UTF_8);
        var serverBytes = "cached-server".getBytes(StandardCharsets.UTF_8);
        var indexBytes = "{\"objects\":{}}".getBytes(StandardCharsets.UTF_8);
        var cache = this.projectDir.resolve("cg-cache");
        this.project.seedLauncherMeta(
                cache,
                "1.12.2",
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
                        """.formatted(
                        DigestUtils.sha1Hex(indexBytes),
                        indexBytes.length,
                        DigestUtils.sha1Hex(clientBytes),
                        clientBytes.length,
                        DigestUtils.sha1Hex(serverBytes),
                        serverBytes.length
                )
        );
        this.project.build(
                """
                import de.undercouch.gradle.tasks.download.Download

                cleanroom {
                    mode = 'vanilla'
                    caches.directory.set(layout.projectDirectory.dir('cg-cache'))
                }
                // gradle-download-task logs via Task.project when it skips existing files in --offline.
                tasks.withType(Download).configureEach { quiet(true) }
                """
        );

        var versionCache = cache.resolve("versions/1.12.2");
        var indexCache = cache.resolve("assets/indexes");
        Files.createDirectories(indexCache);
        Files.write(versionCache.resolve("client.jar"), clientBytes);
        Files.write(versionCache.resolve("server.jar"), serverBytes);
        Files.write(indexCache.resolve("1.12.json"), indexBytes);

        var cached = this.project.runner("downloadClientJar", "downloadServerJar", "downloadAssetIndex", "--offline").build();
        assertThat(cached.task(":downloadClientJar").getOutcome()).isEqualTo(TaskOutcome.SKIPPED);
        assertThat(cached.task(":downloadServerJar").getOutcome()).isEqualTo(TaskOutcome.SKIPPED);
        assertThat(cached.task(":downloadAssetIndex").getOutcome()).isEqualTo(TaskOutcome.SKIPPED);
    }

    @Test
    void assetOutputsAreIndexScoped() throws IOException {
        var assetBytes = "cached-asset".getBytes(StandardCharsets.UTF_8);
        var assetSha1 = DigestUtils.sha1Hex(assetBytes);
        var assetPath = "objects/" + assetSha1.substring(0, 2) + "/" + assetSha1;
        Files.writeString(
                this.projectDir.resolve("asset-index.json"),
                """
                {"objects":{"example":{"hash":"%s","size":%d}}}
                """.formatted(assetSha1, assetBytes.length)
        );
        var cachedAsset = this.projectDir.resolve(assetPath);
        Files.createDirectories(cachedAsset.getParent());
        Files.write(cachedAsset, assetBytes);

        this.project.vanilla(
                """
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
                """.formatted(
                        assetPath
                )
        );

        assertThat(this.project.runner("downloadAssets").build().getOutput()).contains("ASSET_TASK_EXECUTED");

        Files.writeString(this.projectDir.resolve("objects/unrelated-object"), "other index");
        var second = this.project.runner("downloadAssets").build();
        assertThat(second.task(":downloadAssets").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
        assertThat(second.getOutput()).doesNotContain("ASSET_TASK_EXECUTED");
    }

    @Test
    void offlineAssetValidationReportsProblems() throws IOException {
        var missingHash = "1111111111111111111111111111111111111111";
        var corruptHash = "2222222222222222222222222222222222222222";
        Files.writeString(
                this.projectDir.resolve("asset-index.json"),
                """
                {
                  "objects": {
                    "minecraft/sounds/missing.ogg": {"hash":"%s","size":4},
                    "minecraft/textures/corrupt.png": {"hash":"%s","size":7}
                  }
                }
                """.formatted(
                        missingHash,
                        corruptHash
                )
        );
        var corruptAsset = this.projectDir.resolve("objects/22/" + corruptHash);
        Files.createDirectories(corruptAsset.getParent());
        Files.writeString(corruptAsset, "corrupt");
        this.project.vanilla(
                """
                gradle.projectsEvaluated {
                    tasks.named('downloadAssets') {
                        assetIndexFile = layout.projectDirectory.file('asset-index.json')
                        objects = layout.projectDirectory.dir('objects')
                    }
                }
                """
        );

        var result = this.project.runner("downloadAssets", "--offline").buildAndFail();
        assertThat(result.getOutput()).contains("2 Minecraft asset(s) are missing or invalid");
        assertThat(result.getOutput()).contains("minecraft/sounds/missing.ogg: object is missing");
        assertThat(result.getOutput()).contains("minecraft/textures/corrupt.png: SHA-1 does not match");
        assertThat(result.getOutput()).contains("Run downloadAssets once without --offline to repair the shared asset cache");
        this.project.assertProblem("offline-assets");
    }

}
