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

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class RunsDslTest extends BaseFunctionalTest {

    @BeforeEach
    void usePluginClasspath() throws IOException {
        Files.writeString(this.projectDir.resolve("settings.gradle"), """
                plugins { id 'com.cleanroommc.cleanroomgradle.settings' }
                rootProject.name = 'test-project'
                """);
    }

    private GradleRunner runner(String... args) {
        return this.project.runner(args).withPluginClasspath();
    }

    @Test
    void inheritancePrecedesChildConfigurationAndSupportsForwardReferences() throws IOException {
        this.project.build("""
                cleanroom {
                    runs {
                        custom {
                            configure {
                                username = 'Child'
                                args '--child'
                                systemProperty 'shared', 'child'
                                environment 'SHARED', 'child'
                                maxHeapSize = '3G'
                            }
                            inherit = 'base'
                        }
                        sibling { inherit = 'base' }
                        base {
                            configure {
                                username = 'Parent'
                                args '--parent'
                                systemProperty 'shared', 'parent'
                                environment 'SHARED', 'parent'
                                workingDir = layout.projectDirectory.dir('custom-run')
                            }
                            inherit = 'vanillaClient'
                        }
                        vanillaClient {
                            configure {
                                mainClass = 'example.Main'
                                args '--vanilla'
                                jvmArgs '-Dexample=true'
                            }
                        }
                    }
                    mode = 'vanilla'
                }
                tasks.named('runVanillaClient') {
                    throw new GradleException('The parent task must not be realized')
                }
                gradle.projectsEvaluated {
                    def child = tasks.named('runCustom').get()
                    def sibling = tasks.named('runSibling').get()
                    assert child.username.get() == 'Child'
                    assert sibling.username.get() == 'Parent'
                    assert child.args == ['--vanilla', '--parent', '--child']
                    assert sibling.args == ['--vanilla', '--parent']
                    assert child.mainClass.get() == 'example.Main'
                    assert child.systemProperties.shared == 'child'
                    assert child.environment.SHARED == 'child'
                    assert child.maxHeapSize == '3G'
                    assert child.workingDir == file('custom-run')
                    assert child.jvmArgs.contains('-Dexample=true')
                    assert child.side.get().name() == 'CLIENT'
                    assert child.env.get().name() == 'VANILLA'
                    assert child.natives.isPresent()
                }
                """);

        runner("help").build();
    }

    @Test
    void unusedRunConfigurationStaysLazy() throws IOException {
        this.project.vanilla("""
                cleanroom.runs {
                    vanillaClient {
                        configure { throw new GradleException('Unrequested run was configured') }
                    }
                    custom { inherit = 'vanillaClient' }
                }
                """);

        runner("help").build();
    }

    @Test
    void existingRunsInheritDslSettingsWithoutCopyingDirectTaskChanges() throws IOException {
        this.project.vanilla("""
                tasks.named('runVanillaClient') { systemProperty 'direct', 'parent only' }
                cleanroom.runs {
                    named('vanillaClient') {
                        configure {
                            username = 'Parent'
                            args '--parent'
                        }
                    }
                    named('vanillaServer') {
                        configure {
                            username = 'Child'
                            side = 'server'
                            args '--child'
                        }
                        inherit = 'vanillaClient'
                    }
                    configureEach {
                        configure { description = 'Run ' + name }
                    }
                }
                gradle.projectsEvaluated {
                    def client = tasks.named('runVanillaClient').get()
                    def server = tasks.named('runVanillaServer').get()
                    assert client.username.get() == 'Parent'
                    assert server.username.get() == 'Child'
                    assert server.args == ['--parent', '--child']
                    assert server.side.get().name() == 'SERVER'
                    assert !server.systemProperties.containsKey('direct')
                    assert client.systemProperties.direct == 'parent only'
                    assert client.description == 'Run runVanillaClient'
                    assert server.description == 'Run runVanillaServer'
                }
                """);

        runner("help").build();
    }

    @Test
    void kotlinDslSupportsTypedTaskConfiguration() throws IOException {
        Files.writeString(this.projectDir.resolve("build.gradle.kts"), """
                import com.cleanroommc.gradle.api.task.mc.RunMinecraft

                plugins {
                    java
                    id("com.cleanroommc.cleanroomgradle")
                }
                cleanroom {
                    runs {
                        register("custom") {
                            configure(RunMinecraft::class.java) {
                                username.set("Kotlin")
                                jvmArgs("-Dexample=true")
                            }
                            inherit.set("vanillaClient")
                        }
                    }
                    setMode("vanilla")
                }
                afterEvaluate {
                    val custom = tasks.named<RunMinecraft>("runCustom").get()
                    check(custom.username.get() == "Kotlin")
                    check(custom.jvmArgs!!.contains("-Dexample=true"))
                }
                """);

        runner("help").build();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void conditionalRunsAndTheirChildrenFollowAvailability(boolean enabled) throws IOException {
        this.project.loader("""
                cleanroom {
                    runs {
                        ['srgClient', 'srgServer', 'reobfSrgClient', 'reobfSrgServer', 'mcpClient', 'mcpServer'].each { runName ->
                            maybeCreate(runName).configure { description = 'configured ' + name }
                        }
                        custom { inherit = 'srgClient' }
                        descendant { inherit = 'custom' }
                        client {
                            configure { throw new GradleException('Userdev run is unavailable') }
                        }
                    }
                    loader.intermediateRuns = %s
                }
                if (!cleanroom.loader.intermediateRuns.get()) {
                    cleanroom.runs {
                        cleanroomNsightClient { inherit = 'srgClient' }
                        cleanroomClient { inherit = 'cleanroomNsightClient' }
                    }
                }
                gradle.projectsEvaluated {
                    assert tasks.names.contains('runCustom') == %s
                    assert tasks.names.contains('runDescendant') == %s
                    assert !tasks.names.contains('runClient')
                    if (%s) {
                        ['SrgClient', 'SrgServer', 'ReobfSrgClient', 'ReobfSrgServer', 'McpClient', 'McpServer', 'Custom', 'Descendant'].each { suffix ->
                            def task = tasks.named('run' + suffix).get()
                            assert task.description == 'configured ' + task.name
                        }
                        def child = tasks.named('runCustom').get()
                        def descendant = tasks.named('runDescendant').get()
                        def cleanup = tasks.named('discardRunSrgClientIntermediates').get()
                        assert child.finalizedBy.getDependencies(child).contains(cleanup)
                        assert cleanup.mustRunAfter.getDependencies(cleanup).containsAll([child, descendant])
                    }
                }
                """.formatted(enabled, enabled, enabled, enabled));

        runner("help").build();
    }

    @Test
    void loaderAndNsightRunsRetainTheirTaskTypes() throws IOException {
        this.project.loader("""
                import com.cleanroommc.gradle.api.task.mc.NsightExec

                cleanroom.runs {
                    cleanroomClient { configure { username = 'LoaderClient' } }
                    cleanroomServer { configure { username = 'LoaderServer' } }
                    cleanroomNsightClient {
                        configure {
                            activity = 'Trace'
                            ngfxPath = '/example/ngfx'
                            runTaskArguments = ['--offline']
                        }
                    }
                    profile {
                        configure { activity = 'Frame Debugger' }
                        inherit = 'cleanroomNsightClient'
                    }
                }
                gradle.projectsEvaluated {
                    assert tasks.named('runCleanroomClient').get().username.get() == 'LoaderClient'
                    assert tasks.named('runCleanroomServer').get().username.get() == 'LoaderServer'
                    def profile = tasks.named('runProfile', NsightExec).get()
                    assert profile.activity.get() == 'Frame Debugger'
                    assert profile.ngfxPath.get() == '/example/ngfx'
                    assert profile.runTaskName.get() == 'runCleanroomClient'
                    assert profile.runTaskArguments.get() == ['--offline']
                }
                """);

        runner("help").build();
    }

    @Test
    void namedVanillaRunsCanBeConfiguredAndInherited() throws IOException {
        this.project.build("""
                cleanroom {
                    runs {
                        legacyClient { configure { username = 'Legacy' } }
                        legacyServer { configure { maxHeapSize = '2G' } }
                        custom { inherit = 'legacyClient' }
                    }
                    vanilla {
                        legacy { version = '1.12.2' }
                    }
                }
                gradle.projectsEvaluated {
                    def custom = tasks.named('runCustom').get()
                    assert custom.username.get() == 'Legacy'
                    assert custom.minecraftVersion.get() == '1.12.2'
                    assert custom.workingDir == file('run/legacy/vanilla/client')
                    assert tasks.named('runLegacyServer').get().maxHeapSize == '2G'
                }
                """);

        runner("help").build();
    }

    @Test
    void userdevInheritanceRetainsLaunchWiringAndSchedulesOnlyTheChild() throws IOException {
        this.project.build("""
                cleanroom.runs {
                    client { configure { username = 'Modder' } }
                    server { configure { username = 'Server' } }
                    custom { inherit = 'client' }
                }
                dependencies { implementation cleanroom.userdev('0.4.5') }
                afterEvaluate {
                    def custom = tasks.named('runCustom').get()
                    assert custom.username.get() == 'Modder'
                    assert custom.environment.get('MCP_VERSION').toString() == '20201025.185735'
                    assert custom.environment.get('MCP_MAPPINGS').toString() == 'stable_39'
                    assert custom.environment.get('MCP_TO_SRG').toString().endsWith('srg2mcp.tsrg')
                    assert tasks.named('runServer').get().username.get() == 'Server'
                }
                """);

        var args = this.project.userdevModuleArgs("0.4.5", "runCustom", "--dry-run");
        var output = runner(args).build().getOutput();
        PluginBuild.scheduled(output, "runCustom", "classes", "downloadAssets", "extractUserdevSrgToMcp");
        assertThat(output).doesNotContain(":runClient ");
        PluginBuild.reused(runner(args).build().getOutput());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "cycle", "collision", "type", "standaloneType"})
    void invalidDeclarationsFailClearly(String scenario) throws IOException {
        var body = switch (scenario) {
            case "missing" -> "cleanroom.runs { custom { inherit = 'typo' } }";
            case "cycle" -> "cleanroom.runs { first { inherit = 'second' }; second { inherit = 'first' } }";
            case "collision" -> "tasks.register('runCustom'); cleanroom.runs { custom { } }";
            case "type" -> "cleanroom.runs { cleanroomClient { inherit = 'cleanroomNsightClient' } }";
            case "standaloneType" -> "cleanroom.runs { cleanroomNsightClient { inherit = 'standalone' }; standalone { } }";
            default -> throw new IllegalArgumentException(scenario);
        };
        this.project.loader(body);

        var output = runner("help").buildAndFail().getOutput();
        assertThat(output).contains(switch (scenario) {
            case "missing" -> "Unknown inherited run 'typo'";
            case "cycle" -> "Run inheritance cycle first -> second -> first";
            case "collision" -> "conflicts with non-run task 'runCustom'";
            case "type", "standaloneType" -> "their task types differ";
            default -> throw new IllegalArgumentException(scenario);
        });
        this.project.assertProblem("invalid-run");
    }

    @Test
    void standaloneRunExecutesWithInheritedSettingsAndReusesConfigurationCache() throws IOException {
        var source = this.projectDir.resolve("src/main/java/example/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package example;
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Inherited launch " + System.getProperty("example") + " " + args[args.length - 1]);
                    }
                }
                """);
        this.project.build("""
                def runtime = sourceSets.main.runtimeClasspath
                cleanroom.runs {
                    base {
                        configure {
                            side = 'client'
                            env = 'mcp'
                            minecraftVersion = '1.12.2'
                            assetIndexVersion = '1.12'
                            getUUID().set('00000000-0000-0000-0000-000000000000')
                            vanillaAssetsLocation = layout.buildDirectory.dir('assets')
                            mainClass = 'example.Main'
                            classpath = runtime
                            systemProperty 'example', 'parent'
                        }
                    }
                    custom {
                        configure {
                            systemProperty 'example', 'child'
                            args 'argument'
                        }
                        inherit = 'base'
                    }
                }
                tasks.register('runExternal', com.cleanroommc.gradle.api.task.mc.RunMinecraft)
                cleanroom.runs {
                    external { configure { description = 'External run' } }
                }
                gradle.projectsEvaluated {
                    assert tasks.named('runExternal').get().description == 'External run'
                }
                """);

        var output = runner("runCustom").build().getOutput();
        assertThat(output).contains("Inherited launch child argument").doesNotContain(":runBase ");
        output = runner("runCustom").build().getOutput();
        assertThat(output).contains("Inherited launch child argument");
        PluginBuild.reused(output);
    }

}
