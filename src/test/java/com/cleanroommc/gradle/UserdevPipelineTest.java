package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.userdev.ExtractUserdevExtra;
import com.cleanroommc.gradle.api.userdev.MaterializeUserdevClasses;
import com.cleanroommc.gradle.api.userdev.MaterializeUserdevSources;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserdevPipelineTest {

    @TempDir
    Path projectDir;

    private PluginBuild project;

    @BeforeEach
    void setup() throws IOException {
        this.project = new PluginBuild(this.projectDir).settings();
    }

    @Test
    void userdevRegistrationReplacesTheSetupPipeline() throws IOException {
        this.project.build("""
                dependencies {
                    implementation cleanroom.userdev('0.7.0')
                }
                gradle.projectsEvaluated {
                    assert tasks.findByName('setup') == null
                    assert tasks.findByName('packageMinecraftSources') == null
                    assert tasks.findByName('remapDevSrg2Mcp') == null
                    assert tasks.findByName('runClient') != null
                    assert tasks.findByName('runServer') != null
                    assert tasks.findByName('reobfJar') != null
                    assert configurations.findByName('cleanroomUserdev') == null
                }
                """);

        this.project.runner(this.project.userdevModuleArgs("0.7.0", "help")).build();
    }

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
     * The pipeline is registered where the mode is picked, so the buildscript body can reach its tasks by
     * name, and its own configuration runs after the plugin's rather than being overwritten by it.
     */
    @Test
    void runTasksAreConfigurableFromTheBuildscriptBody() throws IOException {
        this.project.build("""
                dependencies {
                    implementation cleanroom.userdev('0.7.0')
                }
                tasks.named('runClient') {
                    description = 'set from the body'
                }
                gradle.projectsEvaluated {
                    assert tasks.named('runClient').get().description == 'set from the body'
                }
                """);

        this.project.runner(this.project.userdevModuleArgs("0.7.0", "help")).build();
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

    @Test
    void specOneUsesTheNewNestedArtifactContract() throws IOException {
        var artifact = this.projectDir.resolve("userdev.jar");
        writeConfig(artifact, """
                {
                  "spec": 1,
                  "minecraft": {
                    "version": "1.12.2",
                    "client": {"url": "https://example.invalid/client.jar", "sha1": "client"},
                    "server": {"url": "https://example.invalid/server.jar", "sha1": "server"}
                  },
                  "loader": {"version": "0.7.0", "forgeVersion": "14.23.5.2860", "group": "com.cleanroommc"},
                  "inputs": {
                    "mcpConfig": "mcp:config:1",
                    "mappings": "mcp:names:1",
                    "initialPatches": "patches:initial:1",
                    "tools": {"accesstransformer":"tools:at:1", "decompiler":"tools:decompiler:1", "mergetool":"tools:merge:1"}
                  },
                  "layout": {
                    "binpatches":"userdev/binpatches.zip","clientBinpatches":"binpatch/client/","serverBinpatches":"binpatch/server/",
                    "obfToSrg":"userdev/obf2srg.tsrg","srgToMcp":"userdev/srg2mcp.tsrg","mcpToSrg":"userdev/mcp2srg.tsrg",
                    "access":"userdev/access.txt","constructors":"userdev/constructors.txt","exceptions":"userdev/exceptions.txt",
                    "methods":"userdev/methods.csv","fields":"userdev/fields.csv","params":"userdev/params.csv",
                    "deobfLibrary":"userdev/deobf-library.jar","sourceInput":"userdev/source-input.jar",
                    "clientExtra":"userdev/client-extra","serverExtra":"userdev/server-extra",
                    "initialPatches":"userdev/initial-patches","accessTransformers":[],"sideAnnotationStrippers":"userdev/cleanroom.sas",
                    "patches":"userdev/patches","loaderSources":"userdev/loader-sources"
                  },
                  "runs": {
                    "client": {"mainClass": "Client", "launchClass": "Launch", "tweakClass": "ClientTweaker", "target": "client"},
                    "server": {"mainClass": "Server", "launchClass": "Launch", "tweakClass": "ServerTweaker", "target": "server"}
                  }
                }
                """);

        var config = UserdevConfig.readFromJar(artifact.toFile());
        assertEquals("1.12.2", config.minecraftVersion());
        assertEquals("0.7.0", config.loaderVersion());
        assertEquals("userdev/mcp2srg.tsrg", config.layout().mcpToSrg());
    }

    @Test
    void previousFlatSpecOneIsRejected() throws IOException {
        var artifact = this.projectDir.resolve("old-userdev.jar");
        writeConfig(artifact, """
                {"spec":1,"minecraftVersion":"1.12.2","cleanroomVersion":"0.7.0","libraries":[]}
                """);

        var failure = assertThrows(IllegalStateException.class,
                () -> UserdevConfig.readFromJar(artifact.toFile()));
        assertTrue(failure.getMessage().contains("minecraft, loader, inputs, layout and runs are required"));
    }

    @Test
    void everyMaterializationOperationIsCacheable() {
        assertTrue(MaterializeUserdevClasses.class.isAnnotationPresent(CacheableTransform.class));
        assertTrue(MaterializeUserdevSources.class.isAnnotationPresent(CacheableTransform.class));
        assertTrue(ExtractUserdevExtra.class.isAnnotationPresent(CacheableTransform.class));
    }

    private static void writeConfig(Path artifact, String json) throws IOException {
        try (var output = new JarOutputStream(Files.newOutputStream(artifact))) {
            output.putNextEntry(new ZipEntry(UserdevConfig.meta(UserdevConfig.FILE_NAME)));
            output.write(json.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

}
