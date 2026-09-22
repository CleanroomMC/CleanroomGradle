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

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectModeTest extends BaseFunctionalTest {

    @Test
    void pluginIsInertUntilAnEnvironmentIsRegistered() throws IOException {
        this.project.build(
                """
                gradle.projectsEvaluated {
                    assert tasks.findByName('runClient') == null
                    assert tasks.findByName('runVanillaClient') == null
                    assert tasks.findByName('setup') == null
                }
                """
        );

        this.project.runner("help").build();
    }

    @Test
    void loaderPreparesPatchDevSourcesBeforeCompiling() throws IOException {
        this.project.build(
                """
                group = 'com.cleanroommc'
                version = '0.1.0'
                cleanroom.mode = 'loader'
                gradle.projectsEvaluated {
                    def minecraft = cleanroom.patches.patchDev.minecraft
                    assert minecraft.input.get().asFile == layout.buildDirectory.dir('cleanroom_gradle/sourceSets/mcp/sources').get().asFile
                    assert minecraft.patches.get().asFile == layout.projectDirectory.dir('module/minecraft/patches').asFile
                    assert minecraft.output.get().asFile == layout.projectDirectory.dir('module/minecraft/src/main/java').asFile
                }
                """
        );

        var output = this.project.runner("userdevJar", "runCleanroomClient", "genBinPatches", "--dry-run").build().getOutput();
        PluginBuild.scheduled(output, "remapSrg2Mcp", "decompileSrg", "initializeMinecraftPatchDevSources", "applySAS", "genBinPatches");
        PluginBuild.notScheduled(output, "genClientBinPatches", "genRuntimeBinPatches");
        assertThat(output.indexOf(":prepareMinecraftPatchDevEnvironment")).isLessThan(output.lastIndexOf(":compileJava"));
        assertThat(output.indexOf(":prepareMcpInjectedSources")).isLessThan(output.lastIndexOf(":compileJava"));

        output = this.project.runner("setup", "compileJava", "--parallel", "--dry-run").build().getOutput();
        assertThat(output.indexOf(":applyMinecraftDiffs")).as(output).isLessThan(output.lastIndexOf(":compileJava"));

        PluginBuild.notScheduled(this.project.runner("compileJava", "--dry-run").build().getOutput(), "applyMinecraftDiffs");
    }

    /**
     * Runs, the userdev config, the MMC pack and the installer all launch the loader, so each reads the loader
     * extension and the Java toolchain instead of keeping its own copy.
     */
    @Test
    void loaderLaunchConsumersReadTheLoaderExtension() throws IOException {
        this.project.build(
                """
                import com.cleanroommc.gradle.api.task.dist.PublishMmcPackZip
                import com.cleanroommc.gradle.api.task.dist.WriteInstallProfile
                import com.cleanroommc.gradle.api.task.dist.WriteUserdevConfig
                import com.cleanroommc.gradle.api.task.mc.RunMinecraft
                group = 'com.cleanroommc'
                version = '0.1.0'
                java.toolchain.languageVersion = JavaLanguageVersion.of(28)
                cleanroom {
                    mode = 'loader'
                    loader {
                        clientMainClass = 'example.ClientMain'
                        serverMainClass = 'example.ServerMain'
                        launchClass = 'example.Launch'
                        clientTweakClass = 'example.ClientTweaker'
                        serverTweakClass = 'example.ServerTweaker'
                        clientTarget = 'exampleClient'
                        serverTarget = 'exampleServer'
                    }
                }
                gradle.projectsEvaluated {
                    def client = tasks.named('runCleanroomClient', RunMinecraft).get()
                    def server = tasks.named('runCleanroomServer', RunMinecraft).get()
                    assert client.mainClass.get() == 'example.ClientMain'
                    assert server.mainClass.get() == 'example.ServerMain'
                    assert client.environment.get('mainClass').toString() == 'example.Launch'
                    assert client.environment.get('tweakClass').toString() == 'example.ClientTweaker'
                    assert server.environment.get('tweakClass').toString() == 'example.ServerTweaker'
                    assert client.environment.get('target').toString() == 'exampleClient'
                    assert server.environment.get('target').toString() == 'exampleServer'
                    // GradleStart renames SRG-named mods into MCP, so MCP_TO_SRG carries a srg-to-mcp file
                    assert client.environment.get('MCP_TO_SRG').toString().endsWith('srg2mcp.tsrg')
                    assert client.environment.get('MCP_VERSION').toString() == '20201025.185735'
                    assert client.environment.get('MCP_MAPPINGS').toString() == 'stable_39'

                    def userdev = tasks.named('writeUserdevConfig', WriteUserdevConfig).get()
                    assert userdev.clientMainClass.get() == 'example.ClientMain'
                    assert userdev.launchClass.get() == 'example.Launch'
                    assert userdev.serverTweakClass.get() == 'example.ServerTweaker'
                    assert userdev.serverTarget.get() == 'exampleServer'

                    def pack = tasks.named('publishMmcPackZip', PublishMmcPackZip).get()
                    assert pack.mainClass.get() == 'example.Launch'
                    assert pack.tweakers.get() == ['example.ClientTweaker']
                    assert pack.compatibleJavaMajors.get() == [28]

                    def installer = tasks.named('writeInstallProfile', WriteInstallProfile).get()
                    assert installer.mainClass.get() == 'example.Launch'
                    assert installer.serverMainClass.get() == 'example.Launch'
                    assert installer.tweakers.get() == ['example.ClientTweaker']
                    assert installer.serverTweakers.get() == ['example.ServerTweaker']
                    assert installer.minimumJava.get() == 28

                    assert tasks.test.workingDir == layout.buildDirectory.dir('test').get().asFile
                    assert configurations.testRuntimeClasspath.extendsFrom.contains(configurations.lwjglNativeCurrent)
                }
                """
        );

        this.project.runner("help").build();
        PluginBuild.notScheduled(this.project.runner("runCleanroomClient", "--dry-run").build().getOutput(), "writeUserdevConfig");
    }

    @Test
    void loaderPipelineReusesConfigurationCache() throws IOException {
        this.project.loader(
                """
                group = 'com.cleanroommc'
                version = '0.1.0'
                """
        );

        var first = this.project.runner("remapSrg2Mcp", "userdevJar", "--dry-run").build().getOutput();
        PluginBuild.scheduled(first, "mergeJars", "splitClientJar", "decompileSrg", "remapSrg2Mcp", "reobfJar", "writeUserdevConfig", "genBinPatches");
        assertThat(first).contains("Configuration cache entry stored");
        PluginBuild.reused(this.project.runner("remapSrg2Mcp", "userdevJar", "--dry-run").build().getOutput());
    }

    @Test
    void intermediateRunsRegisterOnlyWhenAskedFor() throws IOException {
        this.project.loader("cleanroom.loader.intermediateRuns = true");
        var client = this.project.runner("runMcpClient", "--dry-run").build().getOutput();
        PluginBuild.scheduled(client, "compileMcpSourceJava", "remapSrg2Mcp", "downloadAssets", "extractNatives");
        var server = this.project.runner("runVanillaServer", "--dry-run").build().getOutput();
        PluginBuild.scheduled(server, "extractNatives");
        PluginBuild.notScheduled(server, "downloadAssets");

        this.project.loader("");
        var missing = this.project.runner("runMcpClient", "--dry-run").buildAndFail().getOutput();
        assertThat(missing).as(missing).contains("Task 'runMcpClient' not found");
    }

    /**
     * The loader pipeline is registered where the mode is picked, so the buildscript body reaches its tasks
     * by name, and the coordinates it sets afterwards are still the ones the distribution is built under.
     */
    @Test
    void loaderTasksAreConfigurableFromTheBuildscriptBody() throws IOException {
        this.project.build(
                """
                cleanroom.mode = 'loader'
                tasks.named('runCleanroomClient') {
                    description = 'set from the body'
                }
                // Realizing this task before the version is set must not freeze 'unspecified' into it
                tasks.named('installerJar').get()
                group = 'com.cleanroommc'
                version = '0.1.0'
                afterEvaluate {
                    assert tasks.named('runCleanroomClient').get().description == 'set from the body'
                    assert tasks.named('universalJar').get().archiveVersion.get() == '0.1.0'
                    assert tasks.named('installerJar').get().archiveVersion.get() == '0.1.0'
                }
                """
        );

        this.project.plainRunner("help", "--offline").build();
    }

    @Test
    void selectingTwoDifferentEnvironmentsFails() throws IOException {
        this.project.build(
                """
                cleanroom.mode = 'loader'
                dependencies {
                    implementation cleanroom.userdev('0.7.0')
                }
                """
        );

        var output = this.project.plainRunner("help").buildAndFail().getOutput();
        assertThat(output).as(output).contains("environment 'loader' is already registered");
    }

    @Test
    void vanillaEnvironmentNameCannotEscapeItsDirectory() throws IOException {
        this.project.vanilla(
                """
                cleanroom.vanilla {
                    "../escape" { }
                }
                """
        );

        var output = this.project.runner("help").buildAndFail().getOutput();
        assertThat(output).contains("Invalid vanilla environment name '../escape'");
        this.project.assertProblem("invalid-vanilla-environment");
    }

}
