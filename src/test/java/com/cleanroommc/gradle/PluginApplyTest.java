package com.cleanroommc.gradle;

import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginApplyTest {

    @TempDir
    Path projectDir;

    private PluginBuild project;

    @BeforeEach
    void setup() throws IOException {
        this.project = new PluginBuild(this.projectDir).settings();
    }

    @Test
    void appliesLazily() throws IOException {
        this.project.vanilla("");

        var quiet = this.project.runner("help", "--offline").build();
        assertEquals(TaskOutcome.SUCCESS, quiet.task(":help").getOutcome());
        assertFalse(quiet.getOutput().contains("Applying CleanroomGradle"));

        var info = this.project.runner("help", "--info").build();
        assertTrue(info.getOutput().contains("Applying CleanroomGradle"));
    }

    @Test
    void keepsDefaultRepositoriesWhenConsumerDeclaresRepositories() throws IOException {
        this.project.vanilla("""
                repositories {
                    maven {
                        name = 'Consumer'
                        url = 'https://example.invalid/repository/'
                    }
                }
                afterEvaluate {
                    def urls = repositories.findAll { it.hasProperty('url') }.collect { it.url.toString() }
                    assert urls.contains('https://repo.maven.apache.org/maven2/')
                    assert urls.contains('https://libraries.minecraft.net/')
                    assert urls.contains('https://maven.cleanroommc.com/')
                    assert urls.contains('https://example.invalid/repository/')
                }
                """);

        assertEquals(TaskOutcome.SUCCESS, this.project.runner("help", "--offline").build().task(":help").getOutcome());
    }

    @Test
    void restrictsExclusiveGroupsToTheirDefaultRepository() throws IOException {
        this.repositoryArtifact("net.minecraftforge");
        Files.createDirectories(this.projectDir.resolve("empty-repository"));
        this.project.vanilla("""
                repositories {
                    maven {
                        name = 'Consumer'
                        url = layout.projectDirectory.dir('consumer-repository')
                        metadataSources {
                            artifact()
                        }
                    }
                }
                repositories.named('CleanroomMC') {
                    url = layout.projectDirectory.dir('empty-repository')
                }
                configurations {
                    repositoryProbe
                }
                dependencies {
                    repositoryProbe 'net.minecraftforge:probe:1.0'
                }
                tasks.register('resolveRepositoryContent') {
                    inputs.files(configurations.repositoryProbe)
                }
        """);

        var failure = this.project.runner("resolveRepositoryContent", "--offline").buildAndFail();
        assertTrue(failure.getOutput().contains("Could not find net.minecraftforge:probe:1.0"));
    }

    @Test
    void keepsUnfilteredConsumerDuplicateAlongsideExclusiveDefault() throws IOException {
        this.assertDefaultAndConsumerContentResolve("""
                maven {
                    name = 'Consumer Forge'
                    url = layout.projectDirectory.dir('consumer-repository')
                    metadataSources {
                        artifact()
                    }
                }
                """);
    }

    @Test
    void pairsConsumerExclusiveContentWithExclusiveDefault() throws IOException {
        this.assertDefaultAndConsumerContentResolve("""
                exclusiveContent {
                    forRepository {
                        maven {
                            name = 'Consumer Forge'
                            url = layout.projectDirectory.dir('consumer-repository')
                            metadataSources {
                                artifact()
                            }
                        }
                    }
                    filter {
                        includeGroup 'example.consumer'
                    }
                }
                """);
    }

    @Test
    void cleanroomInfoIsConfigurationCacheCompatible() throws IOException {
        this.project.vanilla("""
                cleanroom {
                    caches {
                        directory = layout.projectDirectory.dir('shared-cache')
                        localDirectory = layout.projectDirectory.dir('work-cache')
                    }
                }
                dependencies {
                    decompiler 'example:replacement-decompiler:1.0'
                }
                """);
        var cache = this.projectDir.resolve("shared-cache");
        this.project.seedLauncherMeta(cache, "1.12.2", """
                {
                  "assetIndex": {
                    "id": "1.12",
                    "sha1": "0",
                    "size": 0,
                    "url": "https://example.invalid/1.12.json"
                  },
                  "downloads": {
                    "client": { "sha1": "0", "size": 0, "url": "https://example.invalid/client.jar" },
                    "server": { "sha1": "0", "size": 0, "url": "https://example.invalid/server.jar" }
                  },
                  "id": "1.12.2"
                }
                """);
        Files.writeString(cache.resolve("versions/1.12.2/client.jar"), "cached");

        var first = this.project.runner("cleanroomInfo", "--offline").build();
        assertEquals(TaskOutcome.SUCCESS, first.task(":cleanroomInfo").getOutcome());
        assertTrue(first.getOutput().contains("mode: vanilla"));
        assertTrue(first.getOutput().contains("Minecraft: 1.12.2"));
        assertTrue(first.getOutput().contains("shared cache: " + this.projectDir.resolve("shared-cache")));
        assertTrue(first.getOutput().contains("decompiler: example:replacement-decompiler:1.0"));
        assertTrue(first.getOutput().contains("client jar: ready"));
        assertTrue(first.getOutput().contains("server jar: missing"));

        PluginBuild.reused(this.project.runner("cleanroomInfo", "--offline").build().getOutput());
    }

    @Test
    void missingOfflineVersionMetadataHasRecovery() throws IOException {
        this.project.vanilla("""
                cleanroom {
                    caches.directory = layout.projectDirectory.dir('empty-cache')
                }
                """);
        var cache = this.projectDir.resolve("empty-cache");
        Files.createDirectories(cache);
        Files.writeString(cache.resolve("version_manifest_v2.json"), """
                {"versions":[{"id":"1.12.2","url":"https://example.invalid/version-meta.json","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]}
                """);

        var output = this.project.runner("cleanroomInfo", "--offline").buildAndFail().getOutput();
        assertTrue(output.contains("Gradle is offline and cached metadata for Minecraft 1.12.2 is missing or corrupt at"));
        assertTrue(output.contains("https://example.invalid/version-meta.json"));
        assertTrue(output.contains("Run the requested task once without --offline"));
    }

    private void repositoryArtifact(String group) throws IOException {
        var artifact = this.projectDir.resolve("consumer-repository")
                .resolve(group.replace('.', '/')).resolve("probe/1.0/probe-1.0.jar");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, group);
    }

    private void assertDefaultAndConsumerContentResolve(String consumerRepository) throws IOException {
        this.repositoryArtifact("net.minecraftforge");
        this.repositoryArtifact("example.consumer");
        this.project.vanilla("""
                repositories {
                %s
                }
                repositories.named('CleanroomMC') {
                    url = layout.projectDirectory.dir('consumer-repository')
                }
                configurations {
                    forgeRepositoryProbe
                    consumerRepositoryProbe
                }
                dependencies {
                    forgeRepositoryProbe 'net.minecraftforge:probe:1.0'
                    consumerRepositoryProbe 'example.consumer:probe:1.0'
                }
                tasks.register('resolveRepositoryContent') {
                    inputs.files(configurations.forgeRepositoryProbe, configurations.consumerRepositoryProbe)
                    doLast {
                        assert inputs.files.files.size() == 2
                    }
                }
                afterEvaluate {
                    assert repositories.findAll { it.name in ['CleanroomMC', 'Consumer Forge'] }
                            .collect { it.url }.toSet().size() == 1
                }
                """.formatted(consumerRepository.indent(4)));

        assertEquals(TaskOutcome.SUCCESS, this.project.runner("resolveRepositoryContent", "--offline").build()
                .task(":resolveRepositoryContent").getOutcome());
    }

}
