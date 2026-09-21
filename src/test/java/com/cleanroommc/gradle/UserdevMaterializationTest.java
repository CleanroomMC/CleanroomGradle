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

import com.cleanroommc.gradle.api.schema.UserdevConfig;

import org.junit.jupiter.api.Test;

import org.gradle.testkit.runner.GradleRunner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives both materialization transforms end to end against a raw userdev module, with the decompiler,
 * merge tool, access transformer and renamer replaced by stubs that record what they were handed.
 * Everything between them, binpatching, splitting, metadata injection and archive assembly, is the real
 * pipeline, so this is what proves a published artifact is enough to rebuild a workspace from.
 */
class UserdevMaterializationTest extends BaseFunctionalTest {

    @Test
    void materializesClassesSourcesAndExtras() throws IOException {
        UserdevFixture.seed(this.projectDir, "1.0.0");
        buildScript("1.0.0", "");

        var output = resolve("1.0.0");

        assertThat(entries(file(output, "CLASSES"))).isEqualTo(
                List.of("com/cleanroommc/Loader.class", "net/minecraft/Block.class", "net/minecraft/Patched.class")
        );
        assertThat(entries(file(output, "SOURCES"))).as(() -> entries(file(output, "SOURCES")).toString()).contains("net/minecraft/Block.java");
        assertThat(entries(file(output, "CLIENT_EXTRA"))).isEqualTo(List.of("assets/pack.mcmeta"));
        assertThat(entries(file(output, "SERVER_EXTRA"))).isEqualTo(List.of("assets/server.txt"));
        assertThat(read(file(output, "SOURCES"), "decompiler-classpath.txt")).contains("fixture-library-1.jar");
    }

    @Test
    void appliesTheArtifactsOwnSourcePatches() throws IOException {
        UserdevFixture.seed(this.projectDir, "1.1.0");
        buildScript("1.1.0", "");

        var sources = file(resolve("1.1.0"), "SOURCES");

        assertThat(read(sources, "net/minecraft/Block.java")).isEqualTo("class Block {\n    // patched by the artifact\n}\n");
        assertThat(read(sources, "com/cleanroommc/Loader.java")).isEqualTo("package com.cleanroommc;\n");
    }

    /**
     * The access transformers a workspace declares are transform inputs, so changing one has to rebuild the
     * combined jar, and leaving one alone has to reuse it.
     */
    @Test
    void anAccessTransformerIsPartOfTheTransformIdentity() throws IOException {
        UserdevFixture.seed(this.projectDir, "1.2.0");
        Files.writeString(this.projectDir.resolve("mod_at.cfg"), "public net.minecraft.Block");
        buildScript("1.2.0", "accessTransformers.from('mod_at.cfg')");

        var first = file(resolve("1.2.0"), "CLASSES");
        assertThat(read(first, "access-transformed.txt")).isEqualTo("public net.minecraft.Block");

        var reused = file(resolve("1.2.0"), "CLASSES");
        assertThat(reused).as("the unchanged transform was not reused").isEqualTo(first);

        Files.writeString(this.projectDir.resolve("mod_at.cfg"), "public net.minecraft.Item");
        var rebuilt = file(resolve("1.2.0"), "CLASSES");
        assertThat(read(rebuilt, "access-transformed.txt")).isEqualTo("public net.minecraft.Item");
    }

    @Test
    void aCorruptMinecraftJarInTheSharedCacheIsReported() throws IOException {
        var fixture = new UserdevFixture.Spec();
        fixture.clientSha1 = "0000000000000000000000000000000000000000";
        UserdevFixture.seed(this.projectDir, "1.3.0", fixture);
        buildScript("1.3.0", "");

        var failure = runner("1.3.0", "--offline").buildAndFail().getOutput();
        assertThat(failure).as(failure).contains("missing or corrupt in the shared cache");
    }

    @Test
    void aMissingLayoutEntryIsReportedByName() throws IOException {
        var fixture = new UserdevFixture.Spec();
        fixture.omit = UserdevConfig.meta(UserdevConfig.ACCESS);
        UserdevFixture.seed(this.projectDir, "1.4.0", fixture);
        buildScript("1.4.0", "");

        var failure = runner("1.4.0").buildAndFail().getOutput();
        assertThat(failure).as(failure).contains("Missing required userdev entry userdev/access.txt");
    }

    @Test
    void aWarmWorkspaceMaterializesOffline() throws IOException {
        UserdevFixture.seed(this.projectDir, "1.5.0");
        Files.writeString(this.projectDir.resolve("mod_at.cfg"), "public net.minecraft.Block");
        buildScript("1.5.0", "accessTransformers.from('mod_at.cfg')");
        resolve("1.5.0", "--offline");

        var output = runner("1.5.0", "--offline").build().getOutput();
        PluginBuild.reused(output);
        var classes = file(resolvedFiles(output), "CLASSES");
        assertThat(read(classes, "access-transformed.txt")).isEqualTo("public net.minecraft.Block");
    }

    private Map<String, Path> resolve(String version, String... extra) {
        return resolvedFiles(runner(version, extra).build().getOutput());
    }

    private static Map<String, Path> resolvedFiles(String output) {
        var files = new LinkedHashMap<String, Path>();
        for (var line : output.lines().toList()) {
            var index = line.indexOf(" -> ");
            if (index > 0 && line.startsWith("USERDEV ")) {
                var path = Path.of(line.substring(index + 4));
                if (!path.getFileName().toString().startsWith("fixture-")) {
                    files.put(line.substring("USERDEV ".length(), index), path);
                }
            }
        }
        return files;
    }

    private GradleRunner runner(String version, String... extra) {
        var arguments = new ArrayList<>(
                List.of("resolveUserdev", "-Pcg.repos.enableLocal=true", "-Dmaven.repo.local=" + this.projectDir.resolve("local-maven"))
        );
        arguments.addAll(List.of(extra));
        return this.project.runner(arguments.toArray(String[]::new));
    }

    private static Path file(Map<String, Path> resolved, String role) {
        var file = resolved.get(role);
        assertThat(file != null && Files.isRegularFile(file)).as(() -> role + " was not resolved: " + resolved).isTrue();
        return file;
    }

    private void buildScript(String version, String userdevBody) throws IOException {
        this.project.build(
                UserdevFixture.PREAMBLE + """
                import org.gradle.api.attributes.Category
                import org.gradle.api.attributes.DocsType
                import com.cleanroommc.gradle.api.userdev.UserdevAttributes

                dependencies {
                    implementation cleanroom.userdev('%1$s') {
                        %2$s
                    }
                }

                def probe = { String name, String role, Closure extra ->
                    def configuration = configurations.create('probe' + name)
                    configuration.canBeConsumed = false
                    configuration.canBeResolved = true
                    configuration.attributes {
                        extra(it)
                    }
                    def dependency = dependencies.create('com.cleanroommc:cleanroom-userdev:%1$s')
                    dependency.attributes {
                        it.attribute(UserdevAttributes.STAGE, 'materialized')
                        it.attribute(UserdevAttributes.ROLE, role)
                    }
                    dependencies.add(configuration.name, dependency)
                    return configuration
                }
                def probes = [
                    CLASSES: probe('Classes', 'classes') { },
                    SOURCES: probe('Sources', 'sources') {
                        it.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                        it.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.SOURCES))
                    },
                    CLIENT_EXTRA: probe('ClientExtra', 'client-extra') { },
                    SERVER_EXTRA: probe('ServerExtra', 'server-extra') { },
                ]
                tasks.register('resolveUserdev') {
                    def resolved = probes.collectEntries { name, configuration -> [name, configuration.incoming.files] }
                    doLast {
                        resolved.each { name, files -> files.each { println 'USERDEV ' + name + ' -> ' + it } }
                    }
                }
                """.formatted(version, userdevBody)
        );
    }

    private static List<String> entries(Path jar) {
        try (var zip = new ZipFile(jar.toFile())) {
            return zip.stream().map(ZipEntry::getName).filter(name -> !name.endsWith("/")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path jar, String entry) throws IOException {
        try (var zip = new ZipFile(jar.toFile())) {
            var found = zip.getEntry(entry);
            assertThat(found == null).as(() -> entry + " is missing from " + jar + ": " + entries(jar)).isFalse();
            try (var stream = zip.getInputStream(found)) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

}
