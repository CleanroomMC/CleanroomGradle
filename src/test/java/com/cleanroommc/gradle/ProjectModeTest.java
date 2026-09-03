package com.cleanroommc.gradle;

import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectModeTest extends BaseFunctionalTest {

    @Test
    void pluginIsInertUntilAnEnvironmentIsRegistered() throws IOException {
        this.project.build("""
                gradle.projectsEvaluated {
                    assert tasks.findByName('runClient') == null
                    assert tasks.findByName('runVanillaClient') == null
                    assert tasks.findByName('setup') == null
                    assert configurations.findByName('cleanroomUserdev') == null
                }
                """);

        assertEquals(TaskOutcome.SUCCESS, this.project.runner("help").build().task(":help").getOutcome());
    }

    @Test
    void userdevRegistersModWorkspaceTasks() throws IOException {
        this.project.build("""
                dependencies {
                    implementation cleanroom.userdev('0.4.5') {
                        accessTransformers.from('src/main/resources/META-INF/accesstransformer.cfg')
                    }
                }
                gradle.projectsEvaluated {
                    assert cleanroom.mode.get().name() == 'USERDEV'
                    assert tasks.findByName('setup') == null
                    assert tasks.findByName('runClient') != null
                    assert tasks.findByName('runServer') != null
                    assert tasks.findByName('reobfJar') != null
                    assert tasks.findByName('extractUserdevMcpToSrg') != null
                    assert configurations.findByName('cleanroomUserdev') == null
                    def dependency = configurations.implementation.dependencies.iterator().next()
                    assert dependency.group == 'com.cleanroommc'
                    assert dependency.name == 'cleanroom-userdev'
                    assert dependency.version == '0.4.5'
                }
                """);

        assertEquals(TaskOutcome.SUCCESS,
                this.project.runner(this.project.userdevModuleArgs("0.4.5", "help")).build().task(":help").getOutcome());
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
                    assert tasks.findByName('runSrgClient') == null
                    assert tasks.findByName('compileSrgSourceJava') == null
                    assert tasks.findByName('prepareMinecraftPatchDevEnvironment') != null
                    assert tasks.findByName('runClient') == null

                    def compileJava = tasks.named('compileJava').get()
                    def applyMinecraftDiffs = tasks.named('applyMinecraftDiffs').get()
                    assert compileJava.mustRunAfter.getDependencies(compileJava).contains(applyMinecraftDiffs)
                }
                """);

        var output = this.project.runner("userdevJar", "runCleanroomClient", "genBinPatches", "--dry-run").build().getOutput();
        PluginBuild.scheduled(output,
                "userdevJar", "deobfLibraryJar", "writeMcp2Srg", "writeMcp2Notch", "remapSrg2Mcp", "mergeJars",
                "decompileSrg", "prepareMinecraftPatchDevEnvironment", "initializeMinecraftPatchDevSources",
                "applySAS", "genBinPatches");
        PluginBuild.notScheduled(output, "minecraftClassesJar", "genClientBinPatches", "genRuntimeBinPatches");
        assertTrue(output.indexOf(":prepareMinecraftPatchDevEnvironment") < output.lastIndexOf(":compileJava"));
        assertTrue(output.indexOf(":prepareMcpInjectedSources") < output.lastIndexOf(":compileJava"));

        output = this.project.runner("setup", "compileJava", "test", "--parallel", "--dry-run").build().getOutput();
        assertTrue(output.indexOf(":applyMinecraftDiffs") < output.lastIndexOf(":compileJava"), output);

        output = this.project.runner("compileJava", "--dry-run").build().getOutput();
        PluginBuild.notScheduled(output, "applyMinecraftDiffs");
    }

    @Test
    void loaderLaunchConsumersUseLoaderExtensionAsTheirSingleSource() throws IOException {
        this.project.build("""
                import com.cleanroommc.gradle.api.task.dist.PublishMmcPackZip
                import com.cleanroommc.gradle.api.task.dist.WriteInstallProfile
                import com.cleanroommc.gradle.api.task.mc.RunMinecraft

                group = 'com.cleanroommc'
                version = '0.1.0'
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
                    assert server.environment.get('mainClass').toString() == 'example.Launch'
                    assert client.environment.get('tweakClass').toString() == 'example.ClientTweaker'
                    assert server.environment.get('tweakClass').toString() == 'example.ServerTweaker'
                    assert client.environment.get('target').toString() == 'exampleClient'
                    assert server.environment.get('target').toString() == 'exampleServer'
                    // GradleStart renames SRG-named mods into MCP, so MCP_TO_SRG carries a srg-to-mcp file
                    assert client.environment.get('MCP_TO_SRG').toString().endsWith('srg2mcp.tsrg')
                    assert client.environment.get('MCP_VERSION').toString() == '20201025.185735'
                    assert client.environment.get('MCP_MAPPINGS').toString() == 'stable_39'

                    def pack = tasks.named('publishMmcPackZip', PublishMmcPackZip).get()
                    assert pack.mainClass.get() == 'example.Launch'
                    assert pack.tweakers.get() == ['example.ClientTweaker']
                    assert pack.installerArchiveFile.get().asFile.name == 'mmc-installer.zip'
                    assert tasks.findByName('packageInstallerMmcPackZip') == null
                    def installer = tasks.named('writeInstallProfile', WriteInstallProfile).get()
                    assert installer.mainClass.get() == 'example.Launch'
                    assert installer.serverMainClass.get() == 'example.Launch'
                    assert installer.tweakers.get() == ['example.ClientTweaker']
                    assert installer.serverTweakers.get() == ['example.ServerTweaker']
                }
                """);

        assertEquals(TaskOutcome.SUCCESS, this.project.runner("help").build().task(":help").getOutcome());
        PluginBuild.notScheduled(this.project.runner("runCleanroomClient", "--dry-run").build().getOutput(),
                "writeUserdevConfig");
    }

    @Test
    void loaderOwnsItsLaunchConventions() throws IOException {
        this.project.build("""
                import com.cleanroommc.gradle.api.task.dist.PublishMmcPackZip
                import com.cleanroommc.gradle.api.task.dist.WriteInstallProfile
                import com.cleanroommc.gradle.api.task.dist.WriteUserdevConfig

                group = 'com.cleanroommc'
                version = '0.1.0'
                cleanroom.mode = 'loader'
                gradle.projectsEvaluated {
                    def loader = cleanroom.loader
                    assert loader.clientMainClass.get() == 'com.cleanroommc.boot.MainClient'
                    assert loader.serverMainClass.get() == 'com.cleanroommc.boot.MainServer'
                    assert loader.launchClass.get() == 'top.outlands.foundation.boot.Foundation'
                    assert loader.clientTweakClass.get() == 'net.minecraftforge.fml.common.launcher.FMLTweaker'
                    assert loader.serverTweakClass.get() == 'net.minecraftforge.fml.common.launcher.FMLServerTweaker'
                    assert loader.clientTarget.get() == 'fmldevclient'
                    assert loader.serverTarget.get() == 'fmldevserver'

                    def userdev = tasks.named('writeUserdevConfig', WriteUserdevConfig).get()
                    assert userdev.clientMainClass.get() == loader.clientMainClass.get()
                    assert userdev.serverMainClass.get() == loader.serverMainClass.get()
                    assert userdev.launchClass.get() == loader.launchClass.get()
                    assert userdev.clientTweakClass.get() == loader.clientTweakClass.get()
                    assert userdev.serverTweakClass.get() == loader.serverTweakClass.get()
                    assert userdev.clientTarget.get() == loader.clientTarget.get()
                    assert userdev.serverTarget.get() == loader.serverTarget.get()

                    def pack = tasks.named('publishMmcPackZip', PublishMmcPackZip).get()
                    assert pack.mainClass.get() == loader.launchClass.get()
                    assert pack.tweakers.get() == [loader.clientTweakClass.get()]
                    def installer = tasks.named('writeInstallProfile', WriteInstallProfile).get()
                    assert installer.mainClass.get() == loader.launchClass.get()
                    assert installer.tweakers.get() == [loader.clientTweakClass.get()]
                }
                """);

        assertEquals(TaskOutcome.SUCCESS, this.project.runner("help").build().task(":help").getOutcome());
    }

    @Test
    void loaderJavaTargetPropagatesToDistributionMetadata() throws IOException {
        this.project.build("""
                import com.cleanroommc.gradle.api.task.dist.PublishMmcPackZip
                import com.cleanroommc.gradle.api.task.dist.WriteInstallProfile

                group = 'com.cleanroommc'
                version = '0.1.0'
                java.toolchain.languageVersion = JavaLanguageVersion.of(28)
                cleanroom.mode = 'loader'
                gradle.projectsEvaluated {
                    def pack = tasks.named('publishMmcPackZip', PublishMmcPackZip).get()
                    assert pack.compatibleJavaMajors.get() == [28]
                    def installer = tasks.named('writeInstallProfile', WriteInstallProfile).get()
                    assert installer.minimumJava.get() == 28
                    assert installer.recommendedJava.get() == 28
                }
                """);

        assertEquals(TaskOutcome.SUCCESS, this.project.runner("help").build().task(":help").getOutcome());
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
        PluginBuild.scheduled(first.getOutput(), "userdevJar", "reobfJar", "writeUserdevConfig", "genBinPatches");
        assertTrue(first.getOutput().contains("Configuration cache entry stored"));

        PluginBuild.reused(this.project.runner("userdevJar", "--dry-run").build().getOutput());
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
    void intermediateRunsRegisterOnlyWhenAskedFor() throws IOException {
        this.project.loader("cleanroom.loader.intermediateRuns = true");

        var output = this.project.runner("runMcpClient", "--dry-run").build().getOutput();
        PluginBuild.scheduled(output, "runMcpClient", "compileMcpSourceJava", "remapSrg2Mcp");

        this.project.loader("");
        this.project.runner("help").build();
        var missing = this.project.runner("runMcpClient", "--dry-run").buildAndFail().getOutput();
        assertTrue(missing.contains("Task 'runMcpClient' not found"), missing);
    }

    /**
     * The loader pipeline is registered where the mode is picked, so the buildscript body reaches its tasks
     * by name, its own configuration runs after the plugin's, and the coordinates it sets afterwards are
     * still the ones the distribution is built under.
     */
    @Test
    void loaderTasksAreConfigurableFromTheBuildscriptBody() throws IOException {
        this.project.build("""
                cleanroom.mode = 'loader'
                tasks.named('runCleanroomClient') {
                    description = 'set from the body'
                }
                tasks.named('universalJar') {
                    description = 'also from the body'
                }
                // Realizing this task used to freeze 'unspecified' into its embedded path and manifest.
                tasks.named('installerJar').get()
                group = 'com.cleanroommc'
                version = '0.1.0'
                afterEvaluate {
                    assert tasks.named('runCleanroomClient').get().description == 'set from the body'
                    def universal = tasks.named('universalJar').get()
                    assert universal.description == 'also from the body'
                    assert universal.archiveVersion.get() == '0.1.0'
                    assert tasks.named('installerJar').get().archiveVersion.get() == '0.1.0'
                }
                """);

        this.project.plainRunner("help", "--offline").build();
    }

    @Test
    void selectingTwoDifferentEnvironmentsFailsDirectly() throws IOException {
        this.project.build("""
                cleanroom.mode = 'loader'
                dependencies {
                    implementation cleanroom.userdev('0.7.0')
                }
                """);

        var output = this.project.plainRunner("help").buildAndFail().getOutput();
        assertTrue(output.contains("environment 'loader' is already registered"), output);
        assertTrue(output.contains("only one Cleanroom environment"), output);
    }

    @Test
    void runTasksPrepareAssetsForClientsNotServers() throws IOException {
        this.project.loader("cleanroom.loader.intermediateRuns = true");
        // One client and one server prove the branch; every intermediate run shares the wiring.
        var client = this.project.runner("runVanillaClient", "--dry-run").build().getOutput();
        PluginBuild.scheduled(client, "downloadAssets", "extractNatives");
        var server = this.project.runner("runVanillaServer", "--dry-run").build().getOutput();
        PluginBuild.notScheduled(server, "downloadAssets");
        PluginBuild.scheduled(server, "extractNatives");
    }

}
