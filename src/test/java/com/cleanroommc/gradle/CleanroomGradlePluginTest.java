package com.cleanroommc.gradle;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
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
                        'generateMinecraftDiffs', 'prepareMinecraftPatchDevEnvironment', 'zipMinecraftPatches'
                    ].toSet()
                    assert tasks.reobfJar.group == 'build'
                    assert tasks.downloadAssets.group == null
                    assert tasks.remapSrg2Mcp.group == null
                    assert tasks.writeMcp2Notch.group == null
                    assert tasks.minecraftPatchDevClasses.group == null
                    assert tasks.findByName('srgSourceJar') == null
                    assert tasks.findByName('mcpSourceJar') == null
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

        // The prepare task's validation must survive CC serialization
        var second = runner("prepareExamplePatchDevEnvironment").build();
        assertEquals(TaskOutcome.SUCCESS, second.task(":prepareExamplePatchDevEnvironment").getOutcome());
        assertTrue(second.getOutput().contains("Reusing configuration cache"), "CC not reused on second run. Output:\n" + second.getOutput());
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
        assertTrue(output.contains(":copyMinecraftToSourceSet"), "Minecraft patch-dev source tree is not populated");
        assertTrue(output.contains(":prepareMinecraftPatchDevEnvironment"), "Minecraft patch-dev environment is not prepared");
        assertTrue(output.contains(":prepareMcpInjectedSources"), "MCP annotation source is not prepared");
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
