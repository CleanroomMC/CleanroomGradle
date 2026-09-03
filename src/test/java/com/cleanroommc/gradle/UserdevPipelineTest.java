package com.cleanroommc.gradle;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserdevPipelineTest extends BaseFunctionalTest {

    /**
     * The natives and the renamer's type hierarchy come out of the published module's own graph, so both
     * have to select a variant of it. The artifact itself is kept off the hierarchy: it is MCP-named.
     */
    @Test
    void nativesAndHierarchyResolveFromThePublishedModule() throws IOException {
        this.project.build("""
                dependencies {
                    implementation cleanroom.userdev('0.9.9')
                }
                tasks.register('resolveUserdevGraph') {
                    def natives = configurations._cleanroomUserdevNatives.incoming.files
                    def hierarchy = deobf.srgLibraries
                    doLast {
                        println 'NATIVES ' + natives.files.collect { it.name }
                        println 'HIERARCHY ' + hierarchy.files.collect { it.name }
                    }
                }
                """);

        var output = this.project.plainRunner(this.project.userdevModuleArgs("0.9.9", "resolveUserdevGraph")).build().getOutput();
        assertTrue(output.contains("NATIVES [fixture-native-current-1.jar]"), output);
        assertTrue(!output.contains("fixture-native-foreign-1.jar"), output);
        assertTrue(output.contains("HIERARCHY [fixture-library-1.jar]"), output);
    }

    /**
     * GradleStart renames SRG-named mods into the workspace's own MCP names, so {@code MCP_TO_SRG} carries a
     * srg-to-mcp file in both modes, and the two MCP identifiers are the ones the launcher reports, not the
     * Maven coordinates they are derived from. The loader half of this contract is asserted by
     * {@code ProjectModeTest.loaderLaunchConsumersUseLoaderExtensionAsTheirSingleSource}.
     */
    @Test
    void runsHandGradleStartTheSameMappingsAsLoaderMode() throws IOException {
        this.project.build("""
                import com.cleanroommc.gradle.api.task.mc.RunMinecraft

                dependencies {
                    implementation cleanroom.userdev('0.7.0')
                }
                // Resolving the artifact-owned config needs the project lock, so not projectsEvaluated
                afterEvaluate {
                    def client = tasks.named('runClient', RunMinecraft).get()
                    assert client.environment.get('MCP_VERSION').toString() == '20201025.185735'
                    assert client.environment.get('MCP_MAPPINGS').toString() == 'stable_39'
                    assert client.environment.get('MCP_TO_SRG').toString().endsWith('srg2mcp.tsrg')
                }
                """);

        this.project.runner(this.project.userdevModuleArgs("0.7.0", "help")).build();
    }

    /**
     * The mappings reach GradleStart as an environment value, and those carry no producer information,
     * so the file the run reads has to be depended on by hand.
     */
    @Test
    void runsDependOnTheExtractedMappings() throws IOException {
        this.project.build("""
                dependencies {
                    implementation cleanroom.userdev('0.7.0')
                }
                afterEvaluate {
                    ['runClient', 'runServer'].each { name ->
                        def dependencies = tasks.named(name).get().taskDependencies.getDependencies(null)*.name
                        assert dependencies.contains('extractUserdevSrgToMcp') : name + ' -> ' + dependencies
                    }
                }
                """);

        this.project.runner(this.project.userdevModuleArgs("0.7.0", "help")).build();
    }

    /**
     * The tools a workspace rebuilds sources with are the ones the artifact was produced by, and they
     * arrive as the defaults of the same configurations a loader build overrides.
     */
    @Test
    void toolConfigurationsDefaultToTheArtifactsOwnCoordinates() throws IOException {
        this.project.build("""
                dependencies {
                    implementation cleanroom.userdev('0.7.0')
                }
                // Defaults materialize when the graph resolves, not when the dependency set is read
                tasks.register('readTools') {
                    def tools = ['accesstransformer', 'mergetool', 'decompiler'].collectEntries {
                        [it, configurations.getByName(it).incoming.resolutionResult.rootComponent]
                    }
                    doLast {
                        tools.each { name, root ->
                            println name + ' ' + root.get().dependencies*.requested*.toString()
                        }
                    }
                }
                """);

        var output = this.project.plainRunner(this.project.userdevModuleArgs("0.7.0", "readTools"))
                .build().getOutput();
        assertTrue(output.contains("mergetool [net.minecraftforge:mergetool:1.0]"), output);
        assertTrue(output.contains("accesstransformer [net.minecraftforge:accesstransformers:1.0]"), output);
        assertTrue(output.contains("decompiler [net.minecraftforge:decompiler:1.0]"), output);
    }

    /**
     * Forge's mergetool reads the loader's own classes, which a current toolchain compiles well past the
     * class file version the ASM it ships with understands.
     */
    @Test
    void toolConfigurationsTakeTheLoadedAsm() throws IOException {
        this.project.build("""
                dependencies {
                    implementation cleanroom.userdev('0.7.0')
                }
                afterEvaluate {
                    def forced = configurations.mergetool.resolutionStrategy.forcedModules*.name
                    assert forced.contains('asm') && forced.contains('asm-tree') : forced
                }
                """);

        this.project.runner(this.project.userdevModuleArgs("0.7.0", "help")).build();
    }

    /** A declared dependency replaces the artifact's default, the same way it does in a loader build. */
    @Test
    void declaredToolReplacesTheArtifactsCoordinate() throws IOException {
        this.project.build("""
                dependencies {
                    implementation cleanroom.userdev('0.7.0')
                    mergetool 'example:replacement-merger:2.0'
                }
                tasks.register('readMergetool') {
                    def root = configurations.mergetool.incoming.resolutionResult.rootComponent
                    doLast { println 'MERGETOOL ' + root.get().dependencies*.requested*.toString() }
                }
                """);

        var output = this.project.plainRunner(this.project.userdevModuleArgs("0.7.0", "readMergetool"))
                .build().getOutput();
        assertTrue(output.contains("MERGETOOL [example:replacement-merger:2.0]"), output);
    }

    @Test
    void removedUserdevBlockGivesMigrationGuidance() throws IOException {
        this.project.build("""
                cleanroom {
                    userdev {
                        version = '0.7.0'
                    }
                }
                """);

        var output = this.project.runner("help").buildAndFail().getOutput();
        assertTrue(output.contains("implementation cleanroom.userdev('version')"), output);
    }

    @Test
    void removedUserdevModeGivesMigrationGuidance() throws IOException {
        this.project.build("""
                cleanroom.mode = 'userdev'
                """);

        var output = this.project.runner("help").buildAndFail().getOutput();
        assertTrue(output.contains("registered through dependencies"), output);
        assertTrue(output.contains("implementation cleanroom.userdev('version')"), output);
    }

    @Test
    void removedConfigurationGivesMigrationGuidance() throws IOException {
        this.project.build("""
                dependencies {
                    cleanroomUserdev 'com.cleanroommc:cleanroom:0.7.0:userdev'
                }
                """);

        var output = this.project.runner("help").buildAndFail().getOutput();
        assertTrue(output.contains("cleanroomUserdev configuration was removed"), output);
        assertTrue(output.contains("implementation cleanroom.userdev('version')"), output);
    }

}
