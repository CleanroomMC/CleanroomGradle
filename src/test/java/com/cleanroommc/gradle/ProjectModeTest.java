package com.cleanroommc.gradle;

import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectModeTest {

    @TempDir
    Path projectDir;

    private PluginBuild project;

    @BeforeEach
    void setup() throws IOException {
        this.project = new PluginBuild(this.projectDir).settings();
    }

    @Test
    void defaultUserdevRequiresAnArtifact() throws IOException {
        this.project.build("");

        var missing = this.project.runner("help").buildAndFail();
        assertTrue(missing.getOutput().contains("USERDEV mode requires cleanroom.userdev.version"));
        this.project.assertProblem("missing-userdev");
    }

    @Test
    void userdevRegistersModWorkspaceTasks() throws IOException {
        this.project.build("""
                cleanroom {
                    userdev {
                        version = '0.4.5'
                    }
                }
                gradle.projectsEvaluated {
                    assert tasks.findByName('setup') != null
                    assert tasks.findByName('runClient') != null
                    assert tasks.findByName('decompileDevJar') != null
                    assert tasks.findByName('reobfJar') != null
                    assert tasks.findByName('mergeJars') == null
                    assert tasks.findByName('writeMcp2Notch') == null
                    assert tasks.findByName('runSrgClient') == null
                    assert tasks.findByName('accessTransformDevMcpJar') == null
                    assert tasks.findByName('decompileSrg') == null
                }
                """);

        var info = this.project.runner("cleanroomInfo").build();
        assertEquals(TaskOutcome.SUCCESS, info.task(":cleanroomInfo").getOutcome());
        assertTrue(info.getOutput().contains("mode: userdev"));
        assertTrue(info.getOutput().contains("discard intermediates: true"));
    }

    @Test
    void vanillaDoesNotRegisterMcpOrUserdev() throws IOException {
        this.project.vanilla("""
                gradle.projectsEvaluated {
                    assert tasks.findByName('decompileVersion') != null
                    assert tasks.findByName('runVanillaClient') != null
                    assert tasks.findByName('setup') == null
                    assert tasks.findByName('remapSrg2Mcp') == null
                    assert tasks.findByName('mergeJars') == null
                    assert tasks.findByName('runSrgClient') == null
                    assert tasks.findByName('runClient') == null
                }
                """);

        var result = this.project.runner("cleanroomInfo").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":cleanroomInfo").getOutcome());
        assertTrue(result.getOutput().contains("mode: vanilla"));
    }

    @Test
    void loaderWiresMcpDistributionAndPatchDev() throws IOException {
        this.project.build("""
                group = 'com.cleanroommc'
                version = '0.1.0'
                cleanroom.mode = 'loader'
                gradle.projectsEvaluated {
                    def minecraft = cleanroom.patches.patchDev.minecraft
                    assert cleanroom.caches.discardIntermediates.get() == false
                    assert minecraft.input.get().asFile == layout.buildDirectory.dir('cleanroom_gradle/sourceSets/mcp/sources').get().asFile
                    assert minecraft.patches.get().asFile == layout.projectDirectory.dir('module/minecraft/patches').asFile
                    assert minecraft.output.get().asFile == layout.projectDirectory.dir('module/minecraft/src/main/java').asFile
                    assert tasks.findByName('setup') != null
                    assert tasks.findByName('mergeJars') != null
                    assert tasks.findByName('userdevJar') != null
                    assert tasks.findByName('runCleanroomClient') != null
                    assert tasks.findByName('runSrgClient') != null
                    assert tasks.findByName('prepareMinecraftPatchDevEnvironment') != null
                    assert tasks.findByName('runClient') == null
                }
                """);

        var output = this.project.runner("userdevJar", "runCleanroomClient", "genClientBinPatches", "--dry-run").build().getOutput();
        PluginBuild.scheduled(output,
                "userdevJar", "writeMcp2Srg", "writeMcp2Notch", "remapSrg2Mcp", "mergeJars",
                "decompileSrg", "prepareMinecraftPatchDevEnvironment", "initializeMinecraftPatchDevSources",
                "applySAS", "genClientBinPatches", "genRuntimeBinPatches");
        assertTrue(output.indexOf(":prepareMinecraftPatchDevEnvironment") < output.lastIndexOf(":compileJava"));
        assertTrue(output.indexOf(":prepareMcpInjectedSources") < output.lastIndexOf(":compileJava"));
    }

    @Test
    void loaderPipelineReusesConfigurationCache() throws IOException {
        this.project.loader("");

        var first = this.project.runner("remapSrg2Mcp", "--dry-run").build();
        PluginBuild.scheduled(first.getOutput(),
                "remapSrg2Mcp", "remapNotch2Srg", "mergeJars", "splitClientJar", "extractMcpConfig", "decompileSrg");
        assertTrue(first.getOutput().contains("Configuration cache entry stored"));

        PluginBuild.reused(this.project.runner("remapSrg2Mcp", "--dry-run").build().getOutput());
    }

    @Test
    void loaderDistributionReusesConfigurationCache() throws IOException {
        this.project.build("""
                group = 'com.cleanroommc'
                version = '0.1.0'
                cleanroom {
                    mode = 'loader'
                    patches.developInitial = false
                }
                """);

        var first = this.project.runner("userdevJar", "--dry-run").build();
        PluginBuild.scheduled(first.getOutput(), "userdevJar", "reobfJar", "writeUserdevConfig", "genRuntimeBinPatches");
        assertTrue(first.getOutput().contains("Configuration cache entry stored"));

        PluginBuild.reused(this.project.runner("userdevJar", "--dry-run").build().getOutput());
    }

    @Test
    void vanillaAssembleDoesNotScheduleMcpRemap() throws IOException {
        this.project.vanilla("");
        PluginBuild.notScheduled(this.project.runner("assemble", "--dry-run").build().getOutput(), "remapNotch2Srg", "mergeJars");
    }

    @Test
    void unknownStringModeFails() throws IOException {
        this.project.build("""
                cleanroom {
                    mode = 'nope'
                }
                """);

        var output = this.project.runner("help").buildAndFail().getOutput();
        assertTrue(output.contains("Unknown ProjectMode 'nope'"));
        assertTrue(output.contains("VANILLA, LOADER, USERDEV"));
    }

    @Test
    void runMinecraftTasksIgnoreProcessExit() throws IOException {
        this.project.vanilla("""
                import com.cleanroommc.gradle.api.task.mc.RunMinecraft
                tasks.named('runVanillaClient', RunMinecraft) {
                    side = 'server'
                    env = 'mcp'
                }
                gradle.projectsEvaluated {
                    assert !tasks.withType(RunMinecraft).empty
                    assert tasks.withType(RunMinecraft).every { it.ignoreExitValue }
                    def run = tasks.named('runVanillaClient', RunMinecraft).get()
                    assert run.side.get() == net.minecraftforge.fml.relauncher.Side.SERVER
                    assert run.env.get() == com.cleanroommc.gradle.api.util.Environment.MCP
                }
                """);
        assertEquals(TaskOutcome.SUCCESS, this.project.runner("help").build().task(":help").getOutcome());
    }

    @Test
    void runTasksPrepareAssetsForClientsNotServers() throws IOException {
        this.project.loader("");
        for (var task : List.of("runVanillaClient", "runSrgClient", "runReobfSrgClient", "runMcpClient")) {
            var output = this.project.runner(task, "--dry-run").build().getOutput();
            PluginBuild.scheduled(output, "downloadAssets", "extractNatives");
        }
        for (var task : List.of("runVanillaServer", "runSrgServer", "runReobfSrgServer", "runMcpServer")) {
            var output = this.project.runner(task, "--dry-run").build().getOutput();
            PluginBuild.notScheduled(output, "downloadAssets");
            PluginBuild.scheduled(output, "extractNatives");
        }
    }

}
