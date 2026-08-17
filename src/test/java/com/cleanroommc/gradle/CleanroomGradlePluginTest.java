package com.cleanroommc.gradle;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CleanroomGradlePluginTest {

    // Injected from build.gradle
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

        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }
                group = 'com.example'
                cleanroom {
                    developInitialPatches = false
                }
                """
        );
    }

    private GradleRunner runner(String... args) {
        var allArgs = new ArrayList<>(Arrays.asList(args));
        allArgs.add("--console=plain");
        return GradleRunner.create().withProjectDir(this.projectDir.toFile()).withArguments(allArgs);
    }

    @Test
    void pluginApplies() {
        var result = runner("help").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":help").getOutcome());
        assertTrue(result.getOutput().contains("Running CleanroomGradle"), "plugin did not apply");
    }

    @Test
    void cleanDeletesCleanroomCaches() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                cleanroom {
                    cacheDirectory.set(layout.projectDirectory.dir('cleanroom-cache'))
                    localCacheDirectory.set(layout.projectDirectory.dir('local-cache'))
                }
                """, StandardOpenOption.APPEND);

        var cacheMarker = this.projectDir.resolve("cleanroom-cache/marker");
        var localCacheMarker = this.projectDir.resolve("local-cache/marker");
        Files.createDirectories(cacheMarker.getParent());
        Files.createDirectories(localCacheMarker.getParent());
        Files.createFile(cacheMarker);
        Files.createFile(localCacheMarker);

        var result = runner("clean", "--configuration-cache").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":clean").getOutcome());
        assertFalse(Files.exists(cacheMarker.getParent()), "Shared CleanroomGradle cache was not deleted");
        assertFalse(Files.exists(localCacheMarker.getParent()), "Local CleanroomGradle cache was not deleted");
    }

    @Test
    void discardIntermediatesDeletesConsumedLocalCacheFiles() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"),
                """
                import com.cleanroommc.gradle.api.task.IntermediateProcessor

                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }
                cleanroom {
                    discardIntermediates = true
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
                """
        );
        Files.writeString(this.projectDir.resolve("gradle.properties"),
                "org.gradle.configuration-cache=true\norg.gradle.configuration-cache.problems=fail\n");

        var result = runner("readMid").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":readMid").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":readMidIntermediates").getOutcome());
        assertFalse(Files.exists(this.projectDir.resolve("build/cleanroom_gradle/mid.txt")), "intermediate file was left behind");
    }

    @Test
    void discardIntermediatesOffKeepsConsumedFiles() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                import com.cleanroommc.gradle.api.task.IntermediateProcessor

                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }
                cleanroom {
                    discardIntermediates = false
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
                """);

        var result = runner("readMid").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":readMid").getOutcome());
        assertEquals(TaskOutcome.SKIPPED, result.task(":readMidIntermediates").getOutcome());
        assertEquals("mid", Files.readString(this.projectDir.resolve("build/cleanroom_gradle/mid.txt")));
    }

    @Test
    void helpDoesNotResolveMinecraftMetadata() {
        var result = runner("help", "-Pmc=missing-version-for-lazy-configuration", "--offline").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":help").getOutcome());
    }

    @Test
    void overridingToolConfigurations() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }

                dependencies {
                    decompiler 'example:replacement-decompiler:1.0'
                    mergetool 'example:replacement-merger:1.0'
                    mcinjector 'example:replacement-injector:1.0'
                }

                assert configurations.decompiler.dependencies.iterator().next().name == 'replacement-decompiler'
                assert configurations.mergetool.dependencies.iterator().next().name == 'replacement-merger'
                assert configurations.mcinjector.dependencies.iterator().next().name == 'replacement-injector'
                """
        );

        var result = runner("help").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":help").getOutcome());
    }

    @Test
    void incompatibleToolInvocationCanReplaceDefaultsWithConfigurationCache() throws IOException {
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
                """
        );

        Files.writeString(this.projectDir.resolve("build.gradle"), """
                import com.cleanroommc.gradle.api.task.mcp.MergeJars

                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }

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
                """
        );
        Files.writeString(this.projectDir.resolve("gradle.properties"), "org.gradle.configuration-cache=true\norg.gradle.configuration-cache.problems=fail\n");

        var first = runner("mergeJars").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":mergeJars").getOutcome());
        assertEquals("replacement-tool", Files.readString(this.projectDir.resolve("build/custom-merge.txt")));

        Files.delete(this.projectDir.resolve("build/custom-merge.txt"));
        var second = runner("mergeJars").build();
        assertTrue(second.getOutput().contains("Reusing configuration cache"), "CC not reused on second run. Output:\n" + second.getOutput());
        assertEquals(TaskOutcome.SUCCESS, second.task(":mergeJars").getOutcome());
        assertEquals("replacement-tool", Files.readString(this.projectDir.resolve("build/custom-merge.txt")));
    }

    @Test
    void mcpTasksRegister() {
        // --dry-run resolves only the requested task graph
        var result = runner("remapSrg2Mcp", "--dry-run").build();
        assertTrue(result.getOutput().contains(":remapSrg2Mcp"), "remapSrg2Mcp not present");
        assertTrue(result.getOutput().contains(":remapNotch2Srg"), "remapNotch2Srg not present");
        assertTrue(result.getOutput().contains(":mergeJars"), "mergeJars not present");
        assertTrue(result.getOutput().contains(":splitClientJar"), "splitClientJar not present");
        assertTrue(result.getOutput().contains(":splitServerJar"), "splitServerJar not present");
        assertTrue(result.getOutput().contains(":extractMcpConfig"), "extractMcpConfig not present");
        assertTrue(result.getOutput().contains(":decompileSrg"), "decompileSrg not present");
        assertTrue(result.getOutput().contains(":applyInitialDiffs"), "applyInitialDiffs not present");
    }

    @Test
    void unzipTasksSupportConfigurationCache() throws IOException {
        try (var output = new ZipOutputStream(Files.newOutputStream(this.projectDir.resolve("input.zip")))) {
            output.putNextEntry(new ZipEntry("config/value.txt"));
            output.write("extracted".getBytes());
            output.closeEntry();
        }
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                import com.cleanroommc.gradle.api.task.Tasks

                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }

                Tasks.unzip(project, 'extractTestArchive',
                    layout.projectDirectory.file('input.zip'),
                    layout.buildDirectory.dir('extracted'))
                """);
        Files.writeString(this.projectDir.resolve("gradle.properties"), "org.gradle.configuration-cache=true\norg.gradle.configuration-cache.problems=fail\n");

        var first = runner("extractTestArchive").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":extractTestArchive").getOutcome());
        assertEquals("extracted", Files.readString(this.projectDir.resolve("build/extracted/config/value.txt")));

        Files.delete(this.projectDir.resolve("build/extracted/config/value.txt"));
        var second = runner("extractTestArchive").build();
        assertTrue(second.getOutput().contains("Reusing configuration cache"), "CC not reused on second run. Output:\n" + second.getOutput());
        assertEquals(TaskOutcome.SUCCESS, second.task(":extractTestArchive").getOutcome());
        assertEquals("extracted", Files.readString(this.projectDir.resolve("build/extracted/config/value.txt")));
    }

    @Test
    void runTasksPrepareAssetsForClientsAndNativesForBothSides() {
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
    void runMinecraftAllowsNoNatives() throws IOException {
        var sourceDir = this.projectDir.resolve("src/main/java/example");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("NoNatives.java"), """
                package example;

                public final class NoNatives {
                    public static void main(String[] args) {}
                }
                """);
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                import com.cleanroommc.gradle.api.task.mc.RunMinecraft
                import com.cleanroommc.gradle.api.util.Environment
                import net.minecraftforge.fml.relauncher.Side

                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }

                tasks.register('runWithoutNatives', RunMinecraft) {
                    dependsOn tasks.named('classes')
                    getSide().set(Side.CLIENT)
                    getEnv().set(Environment.MCP)
                    getMainClass().set('example.NoNatives')
                    classpath(sourceSets.main.output)
                    getUUID().set('00000000-0000-0000-0000-000000000000')
                }
                """);

        var result = runner("runWithoutNatives").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":runWithoutNatives").getOutcome());
    }

    @Test
    void loaderTaskGroupsExposeOnlyEntryPoints() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                cleanroom.loaderProject = true

                gradle.projectsEvaluated {
                    assert cleanroom.discardIntermediates.get() == false
                    assert tasks.findAll { it.group == 'vanilla' }*.name.toSet() == [
                        'decompileVersion', 'runVanillaClient', 'runVanillaServer'
                    ].toSet()
                    assert tasks.findAll { it.group == 'MCP' }*.name.toSet() == [
                        'importMcpNames', 'runMcpClient', 'runMcpServer',
                        'runReobfSrgClient', 'runReobfSrgServer', 'runSrgClient', 'runSrgServer'
                    ].toSet()
                    assert tasks.findAll { it.group == 'cleanroom' }*.name.toSet() == [
                        'runCleanroomClient', 'runCleanroomNsightClient', 'runCleanroomServer'
                    ].toSet()
                    assert tasks.findAll { it.group == 'distribution' }*.name.toSet() == [
                        'javadocJar', 'universalJar', 'userdevJar'
                    ].toSet()
                    assert tasks.findAll { it.group == 'minecraft patch development' }*.name.toSet() == [
                        'applyMinecraftDiffs', 'generateMinecraftDiffs',
                        'prepareMinecraftPatchDevEnvironment', 'zipMinecraftPatches'
                    ].toSet()
                    assert tasks.reobfJar.group == 'build'
                    assert tasks.downloadAssets.group == null
                    assert tasks.remapSrg2Mcp.group == null
                    assert tasks.writeMcp2Notch.group == null
                    assert tasks.minecraftPatchDevClasses.group == null
                    assert tasks.findByName('srgSourceJar') == null
                    assert tasks.findByName('mcpSourceJar') == null
                    assert tasks.findByName('copyMinecraftToSourceSet') == null
                    assert tasks.findByName('deobfDataLzma') == null
                    assert tasks.findByName('writeObf2Srg') == null
                    assert tasks.findAll { it.group != null }.every { !it.group.toLowerCase().endsWith(' tasks') }
                }
                """, StandardOpenOption.APPEND);

        assertEquals(TaskOutcome.SUCCESS, runner("help").build().task(":help").getOutcome());
    }

    @Test
    void userdevTaskGroupsExposeOnlyEntryPoints() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                cleanroom.cleanroomVersion = '0.4.5'

                gradle.projectsEvaluated {
                    assert cleanroom.discardIntermediates.get() == true
                    assert tasks.findAll { it.group == 'UserDev' }*.name.toSet() == [
                        'decompileDevJar', 'runClient', 'runServer', 'setupCleanroom'
                    ].toSet()
                    assert tasks.reobfJar.group == 'build'
                    assert tasks.copyUserdev.group == null
                    assert tasks.remapDevSrg2Mcp.group == null
                    assert tasks.findByName('writeMcp2Notch') == null
                }
                """, StandardOpenOption.APPEND);

        assertEquals(TaskOutcome.SUCCESS, runner("help").build().task(":help").getOutcome());
    }

    @Test
    void renameTaskNotPresentOnAssemble() {
        // remapNotch2Srg cannot be compiled into the graph when assemble runs
        var result = runner("assemble", "--dry-run").build();
        assertFalse(result.getOutput().contains(":remapNotch2Srg"), "remapNotch2Srg present when running assemble");
    }

    @Test
    void patchDevEnvironmentWorks() throws IOException {
        Files.createDirectories(this.projectDir.resolve("build/input-src"));
        Files.writeString(this.projectDir.resolve("build/input-src/A.java"), "class A {}\n");
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }
                group = 'com.example'
                cleanroom {
                    patchDev {
                        example {
                            input = layout.buildDirectory.dir('input-src')
                            patches = layout.projectDirectory.dir('custom-patches')
                            output = layout.projectDirectory.dir('custom-output')
                        }
                    }
                }
                """);
        Files.writeString(projectDir.resolve("gradle.properties"), "org.gradle.configuration-cache=true\norg.gradle.configuration-cache.problems=fail\n");

        var first = runner("prepareExamplePatchDevEnvironment").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":prepareExamplePatchDevEnvironment").getOutcome());
        assertTrue(first.getOutput().contains("Configuration cache entry stored"), "CC entry not stored on first run. Output:\n" + first.getOutput());
        var output = this.projectDir.resolve("custom-output/A.java");
        assertTrue(Files.exists(output), "configured patchDev output was not populated");

        Files.writeString(output, "class A { int value; }\n");
        var generated = runner("generateExampleDiffs").build();
        assertEquals(TaskOutcome.SUCCESS, generated.task(":generateExampleDiffs").getOutcome());
        assertTrue(Files.exists(this.projectDir.resolve("custom-patches/A.java.patch")), "configured patches output was not used");

        Files.writeString(output, "class A { int stale; }\n");
        Files.writeString(output.getParent().resolve("Stale.java"), "class Stale {}\n");
        var applied = runner("applyExampleDiffs").build();
        assertEquals(TaskOutcome.SUCCESS, applied.task(":applyExampleDiffs").getOutcome());
        assertEquals("class A { int value; }\n", Files.readString(output),
                "explicit patch application did not replace the populated development source tree");
        assertFalse(Files.exists(output.getParent().resolve("Stale.java")),
                "explicit patch application did not clean stale development sources");

        Files.writeString(output, "class A { int value; int working; }\n");
        // The prepare task's validation must survive CC serialization
        var second = runner("prepareExamplePatchDevEnvironment").build();
        assertEquals(TaskOutcome.SUCCESS, second.task(":prepareExamplePatchDevEnvironment").getOutcome());
        assertTrue(second.getOutput().contains("Reusing configuration cache"), "CC not reused on second run. Output:\n" + second.getOutput());
        assertEquals("class A { int value; int working; }\n", Files.readString(output),
                "preparing the environment overwrote edits in the populated development source tree");
    }

    @Test
    void patchDevTasksCanBeConfiguredImmediately() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                cleanroom.patchDev {
                    example {
                        input = layout.projectDirectory
                    }
                }

                tasks.named('prepareExamplePatchDevEnvironment') {
                    description = 'configured before project evaluation completes'
                }
                """, StandardOpenOption.APPEND);

        var result = runner("help", "--configuration-cache").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":help").getOutcome());
    }

    @Test
    void patchDevInitializationCreatesDeclaredDirectories() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }
                cleanroom {
                    patchDev {
                        example {
                            input = layout.buildDirectory.dir('missing-input')
                            patches = layout.projectDirectory.dir('missing-patches')
                            output = layout.projectDirectory.dir('missing-output')
                        }
                    }
                }
                """);

        var result = runner("prepareExamplePatchDevEnvironment").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":prepareExamplePatchDevEnvironment").getOutcome());
        assertTrue(Files.isDirectory(this.projectDir.resolve("build/missing-input")), "declared input directory was not created");
        assertTrue(Files.isDirectory(this.projectDir.resolve("missing-patches")), "declared patches directory was not created");
        assertTrue(Files.isDirectory(this.projectDir.resolve("missing-output")), "declared output directory was not created");
    }

    @Test
    void loaderProjectRegistersDistributionTasks() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }
                group = 'com.cleanroommc'
                version = '0.1.0'
                cleanroom {
                    loaderProject = true
                }
                gradle.projectsEvaluated {
                    def minecraft = cleanroom.patchDev.minecraft
                    assert minecraft.input.get().asFile == layout.buildDirectory.dir('cleanroom_gradle/sourceSets/mcp/sources').get().asFile
                    assert minecraft.patches.get().asFile == layout.projectDirectory.dir('module/minecraft/patches').asFile
                    assert minecraft.output.get().asFile == layout.projectDirectory.dir('module/minecraft/src/main/java').asFile
                }
                """);

        var result = runner("tasks", "--all").build();
        var output = result.getOutput();
        assertTrue(output.contains("universalJar"), "universalJar not present");
        assertTrue(output.contains("userdevJar"), "userdevJar not present");
        assertTrue(output.contains("javadocJar"), "javadocJar not present");
        assertTrue(output.contains("reobfJar"), "reobfJar not present");
        assertTrue(output.contains("genClientBinPatches"), "genClientBinPatches not present");
        assertTrue(output.contains("genServerBinPatches"), "genServerBinPatches not present");
        assertTrue(output.contains("genRuntimeBinPatches"), "genRuntimeBinPatches not present");
        assertTrue(output.contains("runCleanroomClient"), "runCleanroomClient not present");
        assertTrue(output.contains("runCleanroomServer"), "runCleanroomServer not present");
        assertTrue(output.contains("accessTransformSrgJar"), "accessTransformSrgJar not present");
        assertTrue(output.contains("extractInheritance"), "extractInheritance not present");
        assertTrue(output.contains("checkSAS"), "checkSAS not present");
        assertTrue(output.contains("applySAS"), "applySAS not present");
        assertTrue(output.contains("stripSrgClientJar"), "stripSrgClientJar not present");
        assertTrue(output.contains("stripSrgServerJar"), "stripSrgServerJar not present");
        assertTrue(output.contains("stripClientMinecraftJar"), "stripClientMinecraftJar not present");
        assertTrue(output.contains("stripServerMinecraftJar"), "stripServerMinecraftJar not present");

        var assemble = runner("assemble", "--dry-run").build().getOutput();
        assertTrue(assemble.contains(":universalJar"), "assemble does not build universalJar");
        assertTrue(assemble.contains(":userdevJar"), "assemble does not build userdevJar");
        assertTrue(assemble.contains(":javadocJar"), "assemble does not build javadocJar");
    }

    @Test
    void sideOnlyPipelineDryRunResolvesTaskGraph() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }
                group = 'com.cleanroommc'
                version = '0.1.0'
                cleanroom {
                    loaderProject = true
                }
                """);

        var result = runner("decompileSrg", "runSrgClient", "runSrgServer",
                "genClientBinPatches", "genServerBinPatches", "--dry-run").build();
        var output = result.getOutput();
        assertTrue(output.contains(":extractInheritance"), "inheritance extraction not present");
        assertTrue(output.contains(":checkSAS"), "SAS validation not present");
        assertTrue(output.contains(":applySAS"), "SAS application not present");
        assertTrue(output.contains(":stripSrgClientJar"), "SRG client stripping not present");
        assertTrue(output.contains(":stripSrgServerJar"), "SRG server stripping not present");
        assertTrue(output.contains(":stripClientMinecraftJar"), "release client stripping not present");
        assertTrue(output.contains(":stripServerMinecraftJar"), "release server stripping not present");
        assertTrue(output.contains(":genClientBinPatches"), "client binpatch generation not present");
        assertTrue(output.contains(":genServerBinPatches"), "server binpatch generation not present");
    }

    @Test
    void cleanroomRunPreparesMinecraftPatchDevWorkspace() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }
                cleanroom {
                    loaderProject = true
                }
                """);

        var result = runner("runCleanroomClient", "--dry-run").build();
        var output = result.getOutput();
        assertTrue(output.contains(":decompileSrg"), "Minecraft sources are not decompiled");
        assertTrue(output.contains(":remapSrg2Mcp"), "decompiled sources are not remapped to MCP names");
        assertTrue(output.contains(":prepareMinecraftSources"), "MCP sources are not staged for patch development");
        assertTrue(output.contains(":initializeMinecraftPatchDevSources"), "Minecraft patches are not applied to the patch-dev source tree");
        assertTrue(output.contains(":prepareMinecraftPatchDevEnvironment"), "Minecraft patch-dev environment is not prepared");
        assertTrue(output.contains(":prepareMcpInjectedSources"), "MCP annotation source is not prepared");
        assertTrue(output.indexOf(":initializeMinecraftPatchDevSources") < output.indexOf(":prepareMinecraftPatchDevEnvironment"),
                "Minecraft patches must be applied while initializing the loader patch-dev environment. Output:\n" + output);
        assertTrue(output.indexOf(":prepareMinecraftPatchDevEnvironment") < output.lastIndexOf(":compileJava"),
                "Minecraft patch-dev environment must be prepared before the main sources compile. Output:\n" + output);
        assertTrue(output.indexOf(":prepareMcpInjectedSources") < output.lastIndexOf(":compileJava"),
                "MCP annotation source must be prepared before the main sources compile. Output:\n" + output);
    }

    @Test
    void userdevJarDryRunResolvesTaskGraph() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }
                group = 'com.cleanroommc'
                version = '0.1.0'
                cleanroom {
                    loaderProject = true
                }
                """);

        var result = runner("userdevJar", "--dry-run").build();
        var output = result.getOutput();
        assertTrue(output.contains(":userdevJar"), "userdevJar not present");
        assertTrue(output.contains(":jar"), "jar not present");
        assertTrue(output.contains(":genRuntimeBinPatches"), "genRuntimeBinPatches not present");
        assertTrue(output.contains(":writeMcp2Notch"), "writeMcp2Notch not present");
        assertTrue(output.contains(":writeSrg2Mcp"), "writeSrg2Mcp not present");
        assertTrue(output.contains(":writeMcp2SrgDist"), "writeMcp2SrgDist not present");
    }

    @Test
    void SkipVanillaArtifactsWhenCacheMatches() throws IOException {
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
        Files.writeString(this.projectDir.resolve("build.gradle"),
                """
                import com.cleanroommc.gradle.api.schema.VersionMeta
                import com.cleanroommc.gradle.api.util.IO

                plugins {
                    id 'java'
                    id 'com.cleanroommc.cleanroomgradle'
                }
                cleanroom {
                    cacheDirectory.set(layout.projectDirectory.dir('cg-cache'))
                    versionMeta.set(IO.readJson(file('version-meta.json'), VersionMeta))
                }
                """
        );

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
    void eligibleForConfigurationCache() throws IOException {
        Files.writeString(this.projectDir.resolve("gradle.properties"), "org.gradle.configuration-cache=true\norg.gradle.configuration-cache.problems=warn\n");

        // remapSrg2Mcp --dry-run serializes the whole MCP task chain into the CC snapshot
        var first = runner("remapSrg2Mcp", "--dry-run").build();
        assertTrue(first.getOutput().contains("Configuration cache entry stored"), "CC entry not stored on first run. Output:\n" + first.getOutput());

        // Second run: must reuse snapshot
        var second = runner("remapSrg2Mcp", "--dry-run").build();
        assertTrue(second.getOutput().contains("Reusing configuration cache"), "CC not reused on second run. Output:\n" + second.getOutput());
    }

}
