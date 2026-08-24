package com.cleanroommc.gradle;

import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

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

                    def pack = tasks.named('publishMmcPackZip', PublishMmcPackZip).get()
                    assert pack.mainClass.get() == 'example.Launch'
                    assert pack.tweakers.get() == ['example.ClientTweaker']
                    def embeddedPack = tasks.named('packageInstallerMmcPackZip', Zip).get()
                    assert embeddedPack.archiveFileName.get() == 'mmc-installer.zip'
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
    void installerMmcPackReusesMetadataWithoutTheUniversalPayload() throws IOException {
        this.project.build("""
                apply plugin: 'maven-publish'
                group = 'com.cleanroommc'
                version = '0.1.0'
                cleanroom.mode = 'loader'
                publishing.repositories.maven {
                    url = layout.buildDirectory.dir('fixture-repository')
                }
                gradle.projectsEvaluated {
                    tasks.named('packageInstallerMmcPackZip', Zip) {
                        destinationDirectory = layout.buildDirectory.dir('fixture')
                        archiveFileName = 'thin.zip'
                    }
                }
                """);
        Path source = this.projectDir.resolve("build/libs/cleanroom-0.1.0.zip");
        Files.createDirectories(source.getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(source))) {
            write(zip, "instance.cfg", "instance");
            write(zip, "patches/net.minecraftforge.json", "metadata");
            write(zip, "libraries/patchy-999999.0-empty.jar", "empty");
            write(zip, "libraries/cleanroom-0.1.0-universal.jar", "universal");
        }

        var result = this.project.runner("packageInstallerMmcPackZip", "-x", "publishMmcPackZip").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":packageInstallerMmcPackZip").getOutcome());
        try (var zip = new ZipFile(this.projectDir.resolve("build/fixture/thin.zip").toFile())) {
            Set<String> entries = zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .collect(Collectors.toSet());
            assertEquals(Set.of("instance.cfg", "patches/net.minecraftforge.json",
                    "libraries/patchy-999999.0-empty.jar"), entries);
        }
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

    private static void write(ZipOutputStream zip, String name, String contents) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(contents.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
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
