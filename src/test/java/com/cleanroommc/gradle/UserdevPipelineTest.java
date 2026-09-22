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

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

class UserdevPipelineTest extends BaseFunctionalTest {

    @Test
    void publishesMcpAsMainAndSrgAsClassifierWithoutRenamingLocalFiles() throws IOException {
        var fixture = new UserdevFixture.Spec();
        fixture.srgToMcp = "tsrg2 srg mcp\nexample/Mod example/Mod\n\tfunc_123_a ()V readableName\n";
        var source = this.projectDir.resolve("src/main/java/example/Mod.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package example; public class Mod { public void readableName() {} }");
        Files.writeString(
                this.projectDir.resolve("mcp2srg.tsrg"),
                """
                tsrg2 mcp srg
                example/Mod example/Mod
                \treadableName ()V func_123_a
                """
        );
        this.project.build(
                UserdevFixture.PREAMBLE + """
                apply plugin: 'maven-publish'
                version = '1.0.0'
                base.archivesName = 'modid'
                dependencies { implementation cleanroom.userdev('0.7.0') }
                tasks.named('jar') { archiveAppendix = 'addon' }
                tasks.named('reobfJar') { map.setFrom(file('mcp2srg.tsrg')) }
                publishing {
                    publications {
                        mod(MavenPublication) {
                            artifactId = 'published-mod'
                            from components.java
                            artifact(tasks.reobfJar) { classifier = 'srg' }
                        }
                    }
                    repositories { maven { url = layout.projectDirectory.dir('repo') } }
                }
                """
        );
        var assembleArgs = this.project.userdevModuleArgs("0.7.0", "assemble", "--offline");
        UserdevFixture.seed(this.projectDir, "0.7.0", fixture);
        this.project.runner(assembleArgs).build();
        var localMcp = this.projectDir.resolve("build/libs/modid-addon-1.0.0-mcp.jar");
        var localSrg = this.projectDir.resolve("build/libs/modid-addon-1.0.0.jar");
        var mcpBytes = Files.readAllBytes(localMcp);
        var srgBytes = Files.readAllBytes(localSrg);
        assertThat(methodsIn(localMcp)).contains("readableName").doesNotContain("func_123_a");
        assertThat(methodsIn(localSrg)).contains("func_123_a").doesNotContain("readableName");

        var publishArgs = this.project.userdevModuleArgs("0.7.0", "publish", "--offline");
        UserdevFixture.seed(this.projectDir, "0.7.0", fixture);
        this.project.runner(publishArgs).build();
        PluginBuild.reused(this.project.runner(publishArgs).build().getOutput());
        var published = this.projectDir.resolve("repo/com/example/published-mod/1.0.0");
        assertThat(Files.readAllBytes(published.resolve("published-mod-1.0.0.jar"))).isEqualTo(mcpBytes);
        assertThat(Files.readAllBytes(published.resolve("published-mod-1.0.0-srg.jar"))).isEqualTo(srgBytes);
        assertThat(Files.readAllBytes(localMcp)).isEqualTo(mcpBytes);
        assertThat(Files.readAllBytes(localSrg)).isEqualTo(srgBytes);
        var metadata = JsonParser.parseString(Files.readString(published.resolve("published-mod-1.0.0.module"))).getAsJsonObject();
        assertThat(metadata.getAsJsonArray("variants").asList().stream().map(variant -> variant.getAsJsonObject().get("name").getAsString())).contains(
                "apiElements",
                "runtimeElements"
        );
        for (var variant : metadata.getAsJsonArray("variants")) {
            var value = variant.getAsJsonObject();
            if (List.of("apiElements", "runtimeElements").contains(value.get("name").getAsString())) {
                assertThat(value.getAsJsonArray("files").get(0).getAsJsonObject().get("url").getAsString()).isEqualTo("published-mod-1.0.0.jar");
            }
        }

        Files.writeString(this.projectDir.resolve("settings.gradle"), "\ninclude 'consumer'\n", java.nio.file.StandardOpenOption.APPEND);
        var consumer = this.projectDir.resolve("consumer");
        Files.createDirectories(consumer);
        Files.writeString(
                consumer.resolve("build.gradle"),
                """
                plugins { id 'java'; id 'com.cleanroommc.cleanroomgradle' }
                java.toolchain.languageVersion = JavaLanguageVersion.of(25)
                cleanroom.caches.directory = rootProject.file('shared-cache')
                repositories { maven { url = rootProject.layout.projectDirectory.dir('repo') } }
                configurations { remapped { canBeConsumed = false } }
                dependencies {
                    implementation cleanroom.userdev('0.7.0')
                    implementation project(':')
                    implementation 'com.example:published-mod:1.0.0'
                    remapped deobf('com.example:published-mod:1.0.0:srg')
                }
                tasks.register('resolveModArtifacts') {
                    def runtime = configurations.runtimeClasspath.incoming.files
                    def remapped = configurations.remapped.incoming.files
                    inputs.files(runtime, remapped)
                    doLast {
                        assert runtime.files*.name.containsAll(['modid-addon-1.0.0-mcp.jar', 'published-mod-1.0.0.jar'])
                        assert !runtime.files*.name.contains('modid-addon-1.0.0.jar')
                        remapped.each { println 'REMAPPED ' + it.absolutePath }
                    }
                }
                """
        );
        var consumerSource = consumer.resolve("src/main/java/example/Consumer.java");
        Files.createDirectories(consumerSource.getParent());
        Files.writeString(consumerSource, "package example; public class Consumer { void use(Mod mod) { mod.readableName(); } }");
        var consumerArgs = this.project.userdevModuleArgs("0.7.0", "consumer:compileJava", "consumer:resolveModArtifacts", "--offline");
        UserdevFixture.seed(this.projectDir, "0.7.0", fixture);
        var output = this.project.runner(consumerArgs).build().getOutput();
        var remapped = output.lines()
                .filter(line -> line.startsWith("REMAPPED ") && line.endsWith("published-mod-1.0.0-srg-deobf.jar"))
                .map(line -> Path.of(line.substring("REMAPPED ".length())))
                .findFirst()
                .orElseThrow(() -> new AssertionError(output));
        assertThat(methodsIn(remapped)).contains("readableName").doesNotContain("func_123_a");
    }

    private static List<String> methodsIn(Path path) throws IOException {
        try (var jar = new JarFile(path.toFile()); var input = jar.getInputStream(jar.getJarEntry("example/Mod.class"))) {
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
            return node.methods.stream().map(method -> method.name).toList();
        }
    }

    /**
     * The natives and the renamer's type hierarchy come out of the published module's own graph, so both
     * have to select a variant of it. The artifact itself is kept off the hierarchy: it is MCP-named.
     */
    @Test
    void nativesAndHierarchyResolveFromThePublishedModule() throws IOException {
        this.project.build(
                """
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
                """
        );

        var output = this.project.plainRunner(this.project.userdevModuleArgs("0.9.9", "resolveUserdevGraph")).build().getOutput();
        assertThat(output).as(output).contains("NATIVES [fixture-native-current-1.jar]");
        assertThat(output).as(output).doesNotContain("fixture-native-foreign-1.jar");
        assertThat(output).as(output).contains("HIERARCHY [fixture-library-1.jar]");
    }

    /**
     * GradleStart renames SRG-named mods into the workspace's own MCP names, so {@code MCP_TO_SRG} carries a
     * srg-to-mcp file, and the MCP identifiers are the ones the launcher reports. Environment values carry no
     * producer information, so the run depends on the extracted mappings by hand.
     */
    @Test
    void runsHandGradleStartTheExtractedMappings() throws IOException {
        this.project.build(
                """
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
                    ['runClient', 'runServer'].each { name ->
                        def dependencies = tasks.named(name).get().taskDependencies.getDependencies(null)*.name
                        assert dependencies.contains('extractUserdevSrgToMcp') : name + ' -> ' + dependencies
                    }
                }
                """
        );

        this.project.runner(this.project.userdevModuleArgs("0.7.0", "help")).build();
    }

    /**
     * The tools a workspace rebuilds sources with default to the ones the artifact was produced by, a declared
     * dependency replaces one the same way it does in a loader build. Mergetool takes the loaded ASM because it reads
     * the loader's own classes, which are compiled past what the ASM it ships with understands.
     */
    @Test
    void toolConfigurationsDefaultToTheArtifactsCoordinates() throws IOException {
        this.project.build(
                """
                dependencies {
                    implementation cleanroom.userdev('0.7.0')
                    mergetool 'example:replacement-merger:2.0'
                }
                // Defaults materialize when the graph resolves, not when the dependency set is read
                tasks.register('readTools') {
                    def tools = ['accesstransformer', 'mergetool', 'decompiler'].collectEntries {
                        [it, configurations.getByName(it).incoming.resolutionResult.rootComponent]
                    }
                    def forced = configurations.mergetool.resolutionStrategy.forcedModules*.name
                    doLast {
                        tools.each { name, root ->
                            println name + ' ' + root.get().dependencies*.requested*.toString()
                        }
                        assert forced.containsAll(['asm', 'asm-tree']) : forced
                    }
                }
                """
        );

        var output = this.project.plainRunner(this.project.userdevModuleArgs("0.7.0", "readTools")).build().getOutput();
        assertThat(output).as(output).contains("mergetool [example:replacement-merger:2.0]");
        assertThat(output).as(output).contains("accesstransformer [net.minecraftforge:accesstransformers:1.0]");
        assertThat(output).as(output).contains("decompiler [net.minecraftforge:decompiler:1.0]");
    }

}
