package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.schema.UserdevConfig;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertEquals(List.of("com/cleanroommc/Loader.class", "net/minecraft/Block.class",
                        "net/minecraft/Patched.class"),
                entries(file(output, "CLASSES")));
        assertTrue(entries(file(output, "SOURCES")).contains("net/minecraft/Block.java"),
                () -> entries(file(output, "SOURCES")).toString());
        assertEquals(List.of("assets/pack.mcmeta"), entries(file(output, "CLIENT_EXTRA")));
        assertEquals(List.of("assets/server.txt"), entries(file(output, "SERVER_EXTRA")));
        assertTrue(read(file(output, "SOURCES"), "decompiler-classpath.txt").contains("fixture-library-1.jar"));
    }

    @Test
    void appliesTheArtifactsOwnSourcePatches() throws IOException {
        UserdevFixture.seed(this.projectDir, "1.1.0");
        buildScript("1.1.0", "");

        var sources = file(resolve("1.1.0"), "SOURCES");

        assertEquals("class Block {\n    // patched by the artifact\n}\n", read(sources, "net/minecraft/Block.java"));
        assertEquals("package com.cleanroommc;\n", read(sources, "com/cleanroommc/Loader.java"));
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
        assertEquals("public net.minecraft.Block", read(first, "access-transformed.txt"));

        var reused = file(resolve("1.2.0"), "CLASSES");
        assertEquals(first, reused, "the unchanged transform was not reused");

        Files.writeString(this.projectDir.resolve("mod_at.cfg"), "public net.minecraft.Item");
        var rebuilt = file(resolve("1.2.0"), "CLASSES");
        assertEquals("public net.minecraft.Item", read(rebuilt, "access-transformed.txt"));
    }

    @Test
    void aCorruptMinecraftJarInTheSharedCacheIsReported() throws IOException {
        var fixture = new UserdevFixture.Spec();
        fixture.clientSha1 = "0000000000000000000000000000000000000000";
        UserdevFixture.seed(this.projectDir, "1.3.0", fixture);
        buildScript("1.3.0", "");

        var failure = runner("1.3.0", "--offline").buildAndFail().getOutput();
        assertTrue(failure.contains("missing or corrupt in the shared cache"), failure);
    }

    @Test
    void aMissingLayoutEntryIsReportedByName() throws IOException {
        var fixture = new UserdevFixture.Spec();
        fixture.omit = UserdevConfig.meta(UserdevConfig.ACCESS);
        UserdevFixture.seed(this.projectDir, "1.4.0", fixture);
        buildScript("1.4.0", "");

        var failure = runner("1.4.0").buildAndFail().getOutput();
        assertTrue(failure.contains("Missing required userdev entry userdev/access.txt"), failure);
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
        assertEquals("public net.minecraft.Block", read(classes, "access-transformed.txt"));
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
        var arguments = new ArrayList<>(List.of("resolveUserdev", "-Pcg.repos.enableLocal=true",
                "-Dmaven.repo.local=" + this.projectDir.resolve("local-maven")));
        arguments.addAll(List.of(extra));
        return this.project.runner(arguments.toArray(String[]::new));
    }

    private static Path file(Map<String, Path> resolved, String role) {
        var file = resolved.get(role);
        assertTrue(file != null && Files.isRegularFile(file), () -> role + " was not resolved: " + resolved);
        return file;
    }

    private void buildScript(String version, String userdevBody) throws IOException {
        this.project.build(UserdevFixture.PREAMBLE + """
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
                """.formatted(version, userdevBody));
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
            assertFalse(found == null, () -> entry + " is missing from " + jar + ": " + entries(jar));
            try (var stream = zip.getInputStream(found)) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

}
