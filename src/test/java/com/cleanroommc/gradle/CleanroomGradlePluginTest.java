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
import java.util.ArrayList;
import java.util.Arrays;

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
                    id 'com.cleanroommc.gradle.settings'
                }
                rootProject.name = 'test-project'
                """
                .formatted(PLUGIN_DIR)
        );

        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.gradle'
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
    void overridingToolConfigurations() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.gradle'
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
                    id 'com.cleanroommc.gradle'
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
    void renameTaskNotPresentOnAssemble() {
        // remapNotch2Srg cannot be compiled into the graph when assemble runs
        var result = runner("assemble", "--dry-run").build();
        assertFalse(result.getOutput().contains(":remapNotch2Srg"), "remapNotch2Srg present when running assemble");
    }

    @Test
    void patchDevEnvironmentWorks() throws IOException {
        Files.createDirectories(this.projectDir.resolve("modified-src"));
        Files.writeString(this.projectDir.resolve("modified-src").resolve("A.java"), "class A {}\n");
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.gradle'
                }
                group = 'com.example'
                cleanroom {
                    patchDev {
                        example {
                            source = file('modified-src')
                        }
                    }
                }
                """);
        Files.writeString(projectDir.resolve("gradle.properties"), "org.gradle.configuration-cache=true\norg.gradle.configuration-cache.problems=warn\n");

        var first = runner("prepareExamplePatchDevEnvironment").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":prepareExamplePatchDevEnvironment").getOutcome());

        var dryRun = runner("generateExampleDiffs", "--dry-run").build();
        assertTrue(dryRun.getOutput().contains(":generateExampleDiffs"), "generateExampleDiffs not present");

        // The prepare task's validation must survive CC serialization
        var second = runner("prepareExamplePatchDevEnvironment").build();
        assertEquals(TaskOutcome.SUCCESS, second.task(":prepareExamplePatchDevEnvironment").getOutcome());
    }

    @Test
    void loaderProjectRegistersDistributionTasks() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.gradle'
                }
                group = 'com.cleanroommc'
                version = '0.1.0'
                cleanroom {
                    loaderProject = true
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
                    id 'com.cleanroommc.gradle'
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
    void userdevJarDryRunResolvesTaskGraph() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.cleanroommc.gradle'
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
