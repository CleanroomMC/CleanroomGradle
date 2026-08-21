package com.cleanroommc.gradle;

import org.apache.commons.codec.digest.DigestUtils;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CleanroomGradlePluginTest {

    private static final String PLUGIN_DIR = System.getProperty("plugin.project.dir", new File("").getAbsolutePath()).replace("\\", "/");

    @TempDir
    Path projectDir;

    @BeforeEach
    void setup() throws IOException {
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
                """
                .formatted(PLUGIN_DIR)
        );
        writeVanillaBuild("");
    }

    @Test
    void pluginAppliesWithoutResolvingMetadata() {
        var result = runner("help", "-Pmc=missing-version-for-lazy-configuration", "--offline").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":help").getOutcome());
        assertFalse(result.getOutput().contains("Applying CleanroomGradle"));

        var info = runner("help", "--info").build();
        assertTrue(info.getOutput().contains("Applying CleanroomGradle"));
    }

    @Test
    void userdevIsTheDefaultAndRequiresAnArtifact() throws IOException {
        writeBuild("""
                cleanroom.mode = com.cleanroommc.gradle.api.ext.ProjectMode.USERDEV
                """);

        var missing = runner("help").buildAndFail();
        assertTrue(missing.getOutput().contains("USERDEV mode requires cleanroom.version"),
                "missing userdev input did not have an actionable error");
        assertProblemReported("missing-userdev");

        writeBuild("""
                cleanroom {
                    version = '0.4.5'
                }
                gradle.projectsEvaluated {
                    assert cleanroom.discardIntermediates.get() == true
                    assert tasks.findAll { it.group == 'UserDev' }*.name.toSet() == [
                        'decompileDevJar', 'runClient', 'runServer', 'setup'
                    ].toSet()
                    assert tasks.reobfJar.group == 'build'
                    assert tasks.copyUserdev.group == null
                    assert tasks.remapDevSrg2Mcp.group == null
                    assert tasks.findByName('writeMcp2Notch') == null
                }
                """);

        var info = runner("cleanroomInfo").build();
        assertEquals(TaskOutcome.SUCCESS, info.task(":cleanroomInfo").getOutcome());
        assertTrue(info.getOutput().contains("mode: userdev"));
        assertTrue(info.getOutput().contains("discard intermediates: true"));
    }

    @Test
    void vanillaDoesNotRegisterUserdev() throws IOException {
        writeBuild("""
                import com.cleanroommc.gradle.api.ext.ProjectMode

                cleanroom {
                    mode = ProjectMode.VANILLA
                    version = '0.4.5'
                }
                gradle.projectsEvaluated {
                    assert tasks.findByName('setup') == null
                }
                """);

        var result = runner("cleanroomInfo").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":cleanroomInfo").getOutcome());
        assertTrue(result.getOutput().contains("mode: vanilla"));
    }

    @Test
    void loaderModeWiresDistributionAndPatchDev() throws IOException {
        writeBuild("""
                import com.cleanroommc.gradle.api.ext.ProjectMode

                group = 'com.cleanroommc'
                version = '0.1.0'
                cleanroom.mode = ProjectMode.LOADER
                gradle.projectsEvaluated {
                    def minecraft = cleanroom.patchDev.minecraft
                    assert cleanroom.discardIntermediates.get() == false
                    assert minecraft.input.get().asFile == layout.buildDirectory.dir('cleanroom_gradle/sourceSets/mcp/sources').get().asFile
                    assert minecraft.patches.get().asFile == layout.projectDirectory.dir('module/minecraft/patches').asFile
                    assert minecraft.output.get().asFile == layout.projectDirectory.dir('module/minecraft/src/main/java').asFile
                    assert tasks.findAll { it.group == 'vanilla' }*.name.toSet() == [
                        'decompileVersion', 'runVanillaClient', 'runVanillaServer'
                    ].toSet()
                    assert tasks.findAll { it.group == 'MCP' }*.name.toSet() == [
                        'importMcpNames', 'runMcpClient', 'runMcpServer',
                        'runReobfSrgClient', 'runReobfSrgServer', 'runSrgClient', 'runSrgServer'
                    ].toSet()
                    assert tasks.findAll { it.group == 'cleanroom' }*.name.toSet() == [
                        'setup', 'runCleanroomClient', 'runCleanroomNsightClient', 'runCleanroomServer'
                    ].toSet()
                    assert tasks.findAll { it.group == 'distribution' }*.name.toSet() == [
                        'installerJar', 'javadocJar', 'publishMmcPackZip', 'universalJar', 'userdevJar'
                    ].toSet()
                    assert tasks.findAll { it.group == 'minecraft patch development' }*.name.toSet() == [
                        'applyMinecraftDiffs', 'generateMinecraftDiffs',
                        'prepareMinecraftPatchDevEnvironment', 'zipMinecraftPatches'
                    ].toSet()
                    assert tasks.reobfJar.group == 'build'
                    assert tasks.downloadAssets.group == null
                    assert tasks.remapSrg2Mcp.group == null
                    assert tasks.writeMcp2Notch.group == null
                    assert tasks.findByName('srgSourceJar') == null
                    assert tasks.findByName('mcpSourceJar') == null
                    assert tasks.findAll { it.group != null }.every { !it.group.toLowerCase().endsWith(' tasks') }
                }
                """);

        var result = runner("assemble", "userdevJar", "runCleanroomClient", "runSrgClient", "genClientBinPatches", "--dry-run").build();
        var output = result.getOutput();
        assertTrue(output.contains(":universalJar"));
        assertTrue(output.contains(":userdevJar"));
        assertTrue(output.contains(":javadocJar"));
        assertTrue(output.contains(":publishMmcPackZip"));
        assertTrue(output.contains(":genRuntimeBinPatches"));
        assertTrue(output.contains(":writeMcp2Notch"));
        assertTrue(output.contains(":writeSrg2Mcp"));
        assertTrue(output.contains(":writeMcp2SrgDist"));
        assertTrue(output.contains(":decompileSrg"));
        assertTrue(output.contains(":remapSrg2Mcp"));
        assertTrue(output.contains(":prepareMinecraftSources"));
        assertTrue(output.contains(":initializeMinecraftPatchDevSources"));
        assertTrue(output.contains(":prepareMinecraftPatchDevEnvironment"));
        assertTrue(output.contains(":prepareMcpInjectedSources"));
        assertTrue(output.contains(":extractInheritance"));
        assertTrue(output.contains(":checkSAS"));
        assertTrue(output.contains(":applySAS"));
        assertTrue(output.contains(":stripSrgClientJar"));
        assertTrue(output.contains(":stripClientMinecraftJar"));
        assertTrue(output.contains(":genClientBinPatches"));
        assertTrue(output.indexOf(":initializeMinecraftPatchDevSources") < output.indexOf(":prepareMinecraftPatchDevEnvironment"));
        assertTrue(output.indexOf(":prepareMinecraftPatchDevEnvironment") < output.lastIndexOf(":compileJava"));
        assertTrue(output.indexOf(":prepareMcpInjectedSources") < output.lastIndexOf(":compileJava"));
    }

    @Test
    void namedVanillaEnvironments() throws IOException {
        writeBuild("""
                import com.cleanroommc.gradle.api.task.mc.RunMinecraft

                cleanroom {
                    mode = com.cleanroommc.gradle.api.ext.ProjectMode.USERDEV
                    version = 'test-version'
                    vanilla {
                        "1.12" {
                            client {
                                args '--custom-client'
                                maxHeapSize = '3G'
                            }
                            server {
                                args '--custom-server'
                            }
                        }
                        "26.1" {
                            javaVersion = 25
                        }
                    }
                }
                gradle.projectsEvaluated {
                    assert cleanroom.vanilla.named('1.12').get().version.get() == '1.12'
                    assert cleanroom.vanilla.named('26.1').get().version.get() == '26.1'
                    assert tasks.named('run1.12Client', RunMinecraft).get().args == ['--custom-client']
                    assert tasks.named('run1.12Client', RunMinecraft).get().maxHeapSize == '3G'
                    assert tasks.named('run1.12Server', RunMinecraft).get().args == ['--custom-server']
                    assert tasks.findByName('run26.1Client') != null
                    assert tasks.findByName('download1.12ClientJar') != null
                    assert tasks.findByName('download26.1ClientJar') != null
                    assert configurations.findByName('vanilla1.12') != null
                    assert configurations.findByName('vanilla26.1') != null
                }
                """);

        var first = runner("tasks", "--all", "--configuration-cache").build();
        assertTrue(first.getOutput().contains("run1.12Client"));
        assertTrue(first.getOutput().contains("download1.12Assets"));
        assertTrue(first.getOutput().contains("run26.1Client"));

        var reused = runner("tasks", "--all", "--configuration-cache").build();
        assertTrue(reused.getOutput().contains("Reusing configuration cache"));
    }

    @Test
    void invalidVanillaEnvironmentUsesProblemsApi() throws IOException {
        writeVanillaBuild("""
                cleanroom.vanilla {
                    "../escape" { }
                }
                """);

        var result = runner("help").buildAndFail();
        assertTrue(result.getOutput().contains("Invalid vanilla environment name '../escape'"));
        assertProblemReported("invalid-vanilla-environment");
    }

    @Test
    void cleanPreservesSharedCache() throws IOException {
        writeVanillaBuild("""
                cleanroom {
                    cacheDirectory.set(layout.projectDirectory.dir('cleanroom-cache'))
                    localCacheDirectory.set(layout.projectDirectory.dir('local-cache'))
                }
                """);

        var cacheMarker = this.projectDir.resolve("cleanroom-cache/marker");
        var localCacheMarker = this.projectDir.resolve("local-cache/marker");
        Files.createDirectories(cacheMarker.getParent());
        Files.createDirectories(localCacheMarker.getParent());
        Files.createFile(cacheMarker);
        Files.createFile(localCacheMarker);

        var result = runner("clean").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":clean").getOutcome());
        assertTrue(Files.exists(cacheMarker), "Ordinary clean deleted the shared CleanroomGradle cache");
        assertFalse(Files.exists(localCacheMarker.getParent()), "Local CleanroomGradle cache was not deleted");

        var sharedClean = runner("cleanCleanroomSharedCache").build();
        assertEquals(TaskOutcome.SUCCESS, sharedClean.task(":cleanCleanroomSharedCache").getOutcome());
        assertFalse(Files.exists(cacheMarker.getParent()), "Explicit shared-cache cleanup did not delete the cache");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void discardIntermediates(boolean discard) throws IOException {
        writeBuild("""
                import com.cleanroommc.gradle.api.task.IntermediateProcessor

                cleanroom {
                    mode = com.cleanroommc.gradle.api.ext.ProjectMode.VANILLA
                    discardIntermediates = %s
                    localCacheDirectory.set(layout.buildDirectory.dir('cleanroom_gradle'))
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

        var result = runner("readMid").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":readMid").getOutcome());
        var intermediate = this.projectDir.resolve("build/cleanroom_gradle/mid.txt");
        if (discard) {
            assertEquals(TaskOutcome.SUCCESS, result.task(":readMidIntermediates").getOutcome());
            assertFalse(Files.exists(intermediate), "intermediate file was left behind");
        } else {
            assertEquals(TaskOutcome.SKIPPED, result.task(":readMidIntermediates").getOutcome());
            assertEquals("mid", Files.readString(intermediate));
        }
    }

    @Test
    void diagnosticsReportWithoutResolvingTools() throws IOException {
        writeVanillaBuild("""
                cleanroom {
                    cacheDirectory = layout.projectDirectory.dir('shared-cache')
                    localCacheDirectory = layout.projectDirectory.dir('work-cache')
                }
                dependencies {
                    decompiler 'example:replacement-decompiler:1.0'
                }
                """);
        var clientJar = this.projectDir.resolve("shared-cache/versions/1.12.2/client.jar");
        Files.createDirectories(clientJar.getParent());
        Files.writeString(clientJar, "cached");

        var first = runner("cleanroomInfo", "--offline", "--configuration-cache").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":cleanroomInfo").getOutcome());
        assertTrue(first.getOutput().contains("mode: vanilla"));
        assertTrue(first.getOutput().contains("Minecraft: 1.12.2"));
        assertTrue(first.getOutput().contains("shared cache: " + this.projectDir.resolve("shared-cache")));
        assertTrue(first.getOutput().contains("decompiler: example:replacement-decompiler:1.0"));
        assertTrue(first.getOutput().contains("client jar: ready"));
        assertTrue(first.getOutput().contains("server jar: missing"));

        var second = runner("cleanroomInfo", "--offline", "--configuration-cache").build();
        assertTrue(second.getOutput().contains("Reusing configuration cache"),
                "diagnostics did not reuse the configuration cache. Output:\n" + second.getOutput());
    }

    @Test
    void missingOfflineVersionMetadataHasRecovery() throws IOException {
        writeVanillaBuild("""
                cleanroom {
                    cacheDirectory = layout.projectDirectory.dir('empty-cache')
                    versionMetaUrl = 'https://example.invalid/version-meta.json'
                }
                """);

        var result = runner("cleanroomInfo", "--offline").buildAndFail();
        assertTrue(result.getOutput().contains("Gradle is offline and no cached version metadata exists at"));
        assertTrue(result.getOutput().contains("https://example.invalid/version-meta.json"));
        assertTrue(result.getOutput().contains("Run the requested task once without --offline"));
    }

    @Test
    void mcpPipelineIsConfigurationCacheCompatible() throws IOException {
        enableConfigurationCache();

        var first = runner("remapSrg2Mcp", "--dry-run").build();
        var output = first.getOutput();
        assertTrue(output.contains(":remapSrg2Mcp"));
        assertTrue(output.contains(":remapNotch2Srg"));
        assertTrue(output.contains(":mergeJars"));
        assertTrue(output.contains(":splitClientJar"));
        assertTrue(output.contains(":splitServerJar"));
        assertTrue(output.contains(":extractMcpConfig"));
        assertTrue(output.contains(":decompileSrg"));
        assertTrue(output.contains(":applyInitialDiffs"));
        assertTrue(output.contains("Configuration cache entry stored"));

        var second = runner("remapSrg2Mcp", "--dry-run").build();
        assertTrue(second.getOutput().contains("Reusing configuration cache"));
    }

    @Test
    void runMinecraftTasksIgnoreProcessExit() throws IOException {
        writeVanillaBuild("""
                import com.cleanroommc.gradle.api.task.mc.RunMinecraft
                gradle.projectsEvaluated {
                    assert !tasks.withType(RunMinecraft).empty
                    assert tasks.withType(RunMinecraft).every { it.ignoreExitValue }
                }
                """);

        assertEquals(TaskOutcome.SUCCESS, runner("help").build().task(":help").getOutcome());
    }

    @Test
    void runTasksPrepareAssetsForClientsNotServers() {
        for (var task : List.of("runVanillaClient", "runSrgClient", "runReobfSrgClient", "runMcpClient")) {
            var output = runner(task, "--dry-run").build().getOutput();
            assertTrue(output.contains(":downloadAssets"), task + " does not prepare assets");
            assertTrue(output.contains(":extractNatives"), task + " does not prepare natives");
        }
        for (var task : List.of("runVanillaServer", "runSrgServer", "runReobfSrgServer", "runMcpServer")) {
            var output = runner(task, "--dry-run").build().getOutput();
            assertFalse(output.contains(":downloadAssets"), task + " unnecessarily prepares assets");
            assertTrue(output.contains(":extractNatives"), task + " does not prepare natives");
        }
    }

    @Test
    void assembleDoesNotResolveRemapNotch2Srg() {
        var result = runner("assemble", "--dry-run").build();
        assertFalse(result.getOutput().contains(":remapNotch2Srg"), "remapNotch2Srg present when running assemble");
    }

    @Test
    void patchDevEnvironmentWorks() throws IOException {
        Files.createDirectories(this.projectDir.resolve("build/input-src"));
        Files.writeString(this.projectDir.resolve("build/input-src/A.java"), "class A {}\n");
        writeBuild("""
                cleanroom {
                    mode = com.cleanroommc.gradle.api.ext.ProjectMode.VANILLA
                    patchDev {
                        example {
                            input = layout.buildDirectory.dir('input-src')
                            patches = layout.projectDirectory.dir('custom-patches')
                            output = layout.projectDirectory.dir('custom-output')
                        }
                    }
                }
                tasks.named('prepareExamplePatchDevEnvironment') {
                    description = 'configured before project evaluation completes'
                }
                """);
        enableConfigurationCache();

        var first = runner("prepareExamplePatchDevEnvironment").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":prepareExamplePatchDevEnvironment").getOutcome());
        assertTrue(first.getOutput().contains("Configuration cache entry stored"));
        var output = this.projectDir.resolve("custom-output/A.java");
        assertTrue(Files.exists(output), "configured patchDev output was not populated");
        assertTrue(Files.isDirectory(this.projectDir.resolve("custom-patches")));

        Files.writeString(output, "class A { int value; }\n");
        var generated = runner("generateExampleDiffs").build();
        assertEquals(TaskOutcome.SUCCESS, generated.task(":generateExampleDiffs").getOutcome());
        assertTrue(Files.exists(this.projectDir.resolve("custom-patches/A.java.patch")));

        Files.writeString(output, "class A { int stale; }\n");
        Files.writeString(output.getParent().resolve("Stale.java"), "class Stale {}\n");
        var applied = runner("applyExampleDiffs").build();
        assertEquals(TaskOutcome.SUCCESS, applied.task(":applyExampleDiffs").getOutcome());
        assertEquals("class A { int value; }\n", Files.readString(output));
        assertFalse(Files.exists(output.getParent().resolve("Stale.java")));
        var dirty = this.projectDir.resolve("build/cleanroom_gradle/patchDev/example/dirty");
        assertEquals("class A { int stale; }\n", Files.readString(dirty.resolve("A.java")),
                "edits that no patch describes were not preserved");
        assertEquals("class Stale {}\n", Files.readString(dirty.resolve("Stale.java")));
        assertTrue(applied.getOutput().contains("did not match the current patch set"),
                "applying patches over dirty sources did not warn about them");

        Files.writeString(output, "class A { int value; int working; }\n");
        var second = runner("prepareExamplePatchDevEnvironment").build();
        assertEquals(TaskOutcome.SUCCESS, second.task(":prepareExamplePatchDevEnvironment").getOutcome());
        assertTrue(second.getOutput().contains("Reusing configuration cache"));
        assertEquals("class A { int value; int working; }\n", Files.readString(output),
                "preparing the environment overwrote edits in the populated development source tree");
    }

    @Test
    void downloadsSkipWhenCacheMatches() throws IOException {
        var sourceClient = this.projectDir.resolve("source-client.jar");
        var sourceServer = this.projectDir.resolve("source-server.jar");
        var sourceIndex = this.projectDir.resolve("source-assets.json");
        var clientBytes = "cached-client".getBytes(StandardCharsets.UTF_8);
        var serverBytes = "cached-server".getBytes(StandardCharsets.UTF_8);
        var indexBytes = "{\"objects\":{}}".getBytes(StandardCharsets.UTF_8);
        Files.write(sourceClient, clientBytes);
        Files.write(sourceServer, serverBytes);
        Files.write(sourceIndex, indexBytes);
        var clientSha1 = DigestUtils.sha1Hex(clientBytes);
        var serverSha1 = DigestUtils.sha1Hex(serverBytes);
        var indexSha1 = DigestUtils.sha1Hex(indexBytes);

        Files.writeString(this.projectDir.resolve("version-meta.json"),
                """
                {
                  "assetIndex": {
                    "id": "1.12",
                    "sha1": "%s",
                    "size": %d,
                    "url": "%s"
                  },
                  "downloads": {
                    "client": {
                      "sha1": "%s",
                      "size": %d,
                      "url": "%s"
                    },
                    "server": {
                      "sha1": "%s",
                      "size": %d,
                      "url": "%s"
                    }
                  },
                  "id": "1.12.2"
                }
                """.formatted(indexSha1, indexBytes.length, sourceIndex.toUri(),
                clientSha1, clientBytes.length, sourceClient.toUri(),
                serverSha1, serverBytes.length, sourceServer.toUri()));
        writeBuild("""
                import com.cleanroommc.gradle.api.schema.VersionMeta
                import com.cleanroommc.gradle.api.util.IO

                cleanroom {
                    mode = com.cleanroommc.gradle.api.ext.ProjectMode.VANILLA
                    cacheDirectory.set(layout.projectDirectory.dir('cg-cache'))
                    versionMeta.set(IO.readJson(file('version-meta.json'), VersionMeta))
                }
                """);

        var versionCache = this.projectDir.resolve("cg-cache/versions/1.12.2");
        var indexCache = this.projectDir.resolve("cg-cache/assets/indexes");
        Files.createDirectories(versionCache);
        Files.createDirectories(indexCache);
        Files.write(versionCache.resolve("client.jar"), clientBytes);
        Files.write(versionCache.resolve("server.jar"), serverBytes);
        Files.write(indexCache.resolve("1.12.json"), indexBytes);

        var cached = runner("downloadClientJar", "downloadServerJar", "downloadAssetIndex").build();
        assertEquals(TaskOutcome.SKIPPED, cached.task(":downloadClientJar").getOutcome());
        assertEquals(TaskOutcome.SKIPPED, cached.task(":downloadServerJar").getOutcome());
        assertEquals(TaskOutcome.SKIPPED, cached.task(":downloadAssetIndex").getOutcome());

        Files.writeString(versionCache.resolve("client.jar"), "corrupt");
        Files.writeString(indexCache.resolve("1.12.json"), "corrupt");
        var restored = runner("downloadClientJar", "downloadAssetIndex").build();
        assertEquals(TaskOutcome.SUCCESS, restored.task(":downloadClientJar").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, restored.task(":downloadAssetIndex").getOutcome());
        assertArrayEquals(clientBytes, Files.readAllBytes(versionCache.resolve("client.jar")));
        assertArrayEquals(indexBytes, Files.readAllBytes(indexCache.resolve("1.12.json")));
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

        writeVanillaBuild("""
                tasks.named('downloadAssets') {
                    assetIndexFile = layout.projectDirectory.file('asset-index.json')
                    objects = layout.projectDirectory.dir('objects')
                    doFirst {
                        logger.lifecycle('ASSET_TASK_EXECUTED')
                        assert outputs.files.files == [file('%s')] as Set
                    }
                }
                """.formatted(assetPath));

        var first = runner("downloadAssets").build();
        assertTrue(first.getOutput().contains("ASSET_TASK_EXECUTED"));

        Files.writeString(this.projectDir.resolve("objects/unrelated-object"), "other index");
        var second = runner("downloadAssets").build();
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
        writeVanillaBuild("""
                tasks.named('downloadAssets') {
                    assetIndexFile = layout.projectDirectory.file('asset-index.json')
                    objects = layout.projectDirectory.dir('objects')
                }
                """);

        var result = runner("downloadAssets", "--offline").buildAndFail();
        assertTrue(result.getOutput().contains("2 Minecraft asset(s) are missing or invalid"));
        assertTrue(result.getOutput().contains("minecraft/sounds/missing.ogg: object is missing"));
        assertTrue(result.getOutput().contains("minecraft/textures/corrupt.png: SHA-1 does not match"));
        assertTrue(result.getOutput().contains("Run downloadAssets once without --offline to repair the shared asset cache"));
        assertProblemReported("offline-assets");
    }

    @Test
    void binpatchRoundTripPreservesJarContents() throws IOException {
        var original = this.projectDir.resolve("original.jar");
        var modified = this.projectDir.resolve("modified.jar");
        writeArchive(original, List.of(
                new ArchiveEntry("a/A.class", "old"),
                new ArchiveEntry("b/B.class", "removed"),
                new ArchiveEntry("resource.txt", "resource")));
        writeArchive(modified, List.of(
                new ArchiveEntry("a/A.class", "new class contents"),
                new ArchiveEntry("c/C.class", "added")));

        writeVanillaBuild("""
                import com.cleanroommc.gradle.api.task.patch.ApplyBinPatches
                import com.cleanroommc.gradle.api.task.patch.GenerateBinPatches

                def patches = layout.buildDirectory.file('test.binpatches')
                def generate = tasks.register('generateTestBinPatches', GenerateBinPatches) {
                    originalJar = layout.projectDirectory.file('original.jar')
                    modifiedJar = layout.projectDirectory.file('modified.jar')
                    includedPrefixes = []
                    binpatches = patches
                }
                tasks.register('applyTestBinPatches', ApplyBinPatches) {
                    dependsOn generate
                    originalJar = layout.projectDirectory.file('original.jar')
                    binpatches = patches
                    patchedJar = layout.buildDirectory.file('patched.jar')
                }
                """);

        var result = runner("applyTestBinPatches").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateTestBinPatches").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":applyTestBinPatches").getOutcome());
        try (var zip = new ZipFile(this.projectDir.resolve("build/patched.jar").toFile())) {
            assertEquals("new class contents", readEntry(zip, "a/A.class"));
            assertNull(zip.getEntry("b/B.class"));
            assertEquals("added", readEntry(zip, "c/C.class"));
            assertEquals("resource", readEntry(zip, "resource.txt"));
        }
    }

    @Test
    void customToolInvocationReusesConfigurationCache() throws IOException {
        var sourceDir = this.projectDir.resolve("src/main/java/example");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("CustomMerge.java"), """
                package example;

                import java.nio.file.Files;
                import java.nio.file.Path;

                public final class CustomMerge {
                    public static void main(String[] args) throws Exception {
                        var output = Path.of(args[0]);
                        Files.createDirectories(output.getParent());
                        Files.writeString(output, args[1]);
                    }
                }
                """);
        writeVanillaBuild("""
                import com.cleanroommc.gradle.api.task.mcp.MergeJars

                def customOutput = layout.buildDirectory.file('custom-merge.txt')
                tasks.named('mergeJars', MergeJars) {
                    setDependsOn([tasks.named('classes')])
                    toolClasspath.setFrom(sourceSets.main.output)
                    useDefaultToolArguments = false
                    mainClass = 'example.CustomMerge'
                    setArgs([customOutput.get().asFile.absolutePath, 'replacement-tool'])

                    clientJar = layout.projectDirectory.file('build.gradle')
                    serverJar = layout.projectDirectory.file('build.gradle')
                    srgMappingFile = layout.projectDirectory.file('build.gradle')
                    minecraftVersion = 'replacement'
                    mergedJar = customOutput
                }
                """);
        enableConfigurationCache();

        var first = runner("mergeJars").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":mergeJars").getOutcome());
        assertEquals("replacement-tool", Files.readString(this.projectDir.resolve("build/custom-merge.txt")));

        Files.delete(this.projectDir.resolve("build/custom-merge.txt"));
        var second = runner("mergeJars").build();
        assertTrue(second.getOutput().contains("Reusing configuration cache"));
        assertEquals(TaskOutcome.SUCCESS, second.task(":mergeJars").getOutcome());
        assertEquals("replacement-tool", Files.readString(this.projectDir.resolve("build/custom-merge.txt")));
    }

    private void writeVanillaBuild(String extra) throws IOException {
        writeBuild("""
                cleanroom {
                    mode = com.cleanroommc.gradle.api.ext.ProjectMode.VANILLA
                    developInitialPatches = false
                }
                """ + extra);
    }

    private void writeBuild(String body) throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }
                group = 'com.example'
                """ + body);
    }

    private void enableConfigurationCache() throws IOException {
        Files.writeString(this.projectDir.resolve("gradle.properties"),
                "org.gradle.configuration-cache=true\norg.gradle.configuration-cache.problems=fail\n");
    }

    private GradleRunner runner(String... args) {
        var allArgs = new ArrayList<>(Arrays.asList(args));
        allArgs.add("--console=plain");
        var runner = GradleRunner.create().withProjectDir(this.projectDir.toFile()).withArguments(allArgs);
        var testKitHome = System.getProperty("testkit.gradle.user.home");
        if (testKitHome != null) {
            runner.withTestKitDir(new File(testKitHome));
        }
        return runner;
    }

    private void assertProblemReported(String problemId) throws IOException {
        var report = this.projectDir.resolve("build/reports/problems/problems-report.html");
        assertTrue(Files.isRegularFile(report), "Gradle Problems report was not generated");
        assertTrue(Files.readString(report).contains(problemId),
                "Problems report does not contain '" + problemId + "'");
    }

    private static void writeArchive(Path output, List<ArchiveEntry> entries) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(output))) {
            for (var entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.contents().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static String readEntry(ZipFile zip, String name) throws IOException {
        try (var input = zip.getInputStream(zip.getEntry(name))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record ArchiveEntry(String name, String contents) { }

}
