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

import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class PluginApplyTest extends BaseFunctionalTest {

    @Test
    void appliesLazily() throws IOException {
        this.project.vanilla("");

        var quiet = this.project.runner("help", "--offline").build();
        assertThat(quiet.task(":help").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(quiet.getOutput()).doesNotContain("Applying CleanroomGradle");

        var info = this.project.runner("help", "--info").build();
        assertThat(info.getOutput()).contains("Applying CleanroomGradle");
    }

    @Test
    void keepsDefaultRepositoriesWhenConsumerDeclaresRepositories() throws IOException {
        this.project.vanilla(
                """
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
                """
        );

        assertThat(this.project.runner("help", "--offline").build().task(":help").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void restrictsExclusiveGroupsToTheirDefaultRepository() throws IOException {
        this.repositoryArtifact("net.minecraftforge");
        Files.createDirectories(this.projectDir.resolve("empty-repository"));
        this.project.vanilla(
                """
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
        """
        );

        var failure = this.project.runner("resolveRepositoryContent", "--offline").buildAndFail();
        assertThat(failure.getOutput()).contains("Could not find net.minecraftforge:probe:1.0");
    }

    @Test
    void keepsUnfilteredConsumerDuplicateAlongsideExclusiveDefault() throws IOException {
        this.assertDefaultAndConsumerContentResolve(
                """
                maven {
                    name = 'Consumer Forge'
                    url = layout.projectDirectory.dir('consumer-repository')
                    metadataSources {
                        artifact()
                    }
                }
                """
        );
    }

    @Test
    void pairsConsumerExclusiveContentWithExclusiveDefault() throws IOException {
        this.assertDefaultAndConsumerContentResolve(
                """
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
                """
        );
    }

    @Test
    void cleanroomInfoIsConfigurationCacheCompatible() throws IOException {
        this.project.vanilla(
                """
                cleanroom {
                    caches {
                        directory = layout.projectDirectory.dir('shared-cache')
                        localDirectory = layout.projectDirectory.dir('work-cache')
                    }
                }
                dependencies {
                    decompiler 'example:replacement-decompiler:1.0'
                }
                """
        );
        var cache = this.projectDir.resolve("shared-cache");
        this.project.seedLauncherMeta(
                cache,
                "1.12.2",
                """
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
                """
        );
        Files.writeString(cache.resolve("versions/1.12.2/client.jar"), "cached");

        var first = this.project.runner("cleanroomInfo", "--offline").build();
        assertThat(first.task(":cleanroomInfo").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(first.getOutput()).contains("mode: vanilla");
        assertThat(first.getOutput()).contains("Minecraft: 1.12.2");
        assertThat(first.getOutput()).contains("shared cache: " + this.projectDir.resolve("shared-cache"));
        assertThat(first.getOutput()).contains("decompiler: example:replacement-decompiler:1.0");
        assertThat(first.getOutput()).contains("client jar: ready");
        assertThat(first.getOutput()).contains("server jar: missing");

        PluginBuild.reused(this.project.runner("cleanroomInfo", "--offline").build().getOutput());
    }

    @Test
    void missingOfflineVersionMetadataHasRecovery() throws IOException {
        this.project.vanilla(
                """
                cleanroom {
                    caches.directory = layout.projectDirectory.dir('empty-cache')
                }
                """
        );
        var cache = this.projectDir.resolve("empty-cache");
        Files.createDirectories(cache);
        Files.writeString(
                cache.resolve("version_manifest_v2.json"),
                """
                {"versions":[{"id":"1.12.2","url":"https://example.invalid/version-meta.json","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]}
                """
        );

        var output = this.project.runner("cleanroomInfo", "--offline").buildAndFail().getOutput();
        assertThat(output).contains("Gradle is offline and cached metadata for Minecraft 1.12.2 is missing or corrupt at");
        assertThat(output).contains("https://example.invalid/version-meta.json");
        assertThat(output).contains("Run the requested task once without --offline");
    }

    private void repositoryArtifact(String group) throws IOException {
        var artifact = this.projectDir.resolve("consumer-repository").resolve(group.replace('.', '/')).resolve("probe/1.0/probe-1.0.jar");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, group);
    }

    private void assertDefaultAndConsumerContentResolve(String consumerRepository) throws IOException {
        this.repositoryArtifact("net.minecraftforge");
        this.repositoryArtifact("example.consumer");
        this.project.vanilla(
                """
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
                """.formatted(
                        consumerRepository.indent(4)
                )
        );

        assertThat(this.project.runner("resolveRepositoryContent", "--offline").build().task(":resolveRepositoryContent").getOutcome()).isEqualTo(
                TaskOutcome.SUCCESS
        );
    }

}
