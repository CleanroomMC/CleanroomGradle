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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.gradle.testkit.runner.TaskOutcome;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class PluginApplyTest extends BaseFunctionalTest {

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

    @ParameterizedTest
    @ValueSource(
            strings = {
                    """
                    maven {
                        name = 'Consumer Forge'
                        url = layout.projectDirectory.dir('consumer-repository')
                        metadataSources { artifact() }
                    }
                    """,
                    """
                    exclusiveContent {
                        forRepository {
                            maven {
                                name = 'Consumer Forge'
                                url = layout.projectDirectory.dir('consumer-repository')
                                metadataSources { artifact() }
                            }
                        }
                        filter { includeGroup 'example.consumer' }
                    }
                    """
            }
    )
    void consumerDuplicatesOfADefaultRepositoryStillResolve(String consumerRepository) throws IOException {
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

}
