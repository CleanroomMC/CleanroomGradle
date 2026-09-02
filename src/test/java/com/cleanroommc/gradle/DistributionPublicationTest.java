package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.util.Platform;
import org.apache.commons.lang3.StringUtils;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The installer and the MMC pack have to travel with the rest of the distribution, and a userdev workspace
 * has to be able to ask the published module for {@code :userdev} and {@code :sources}.
 */
class DistributionPublicationTest {

    @TempDir
    Path projectDir;

    private PluginBuild project;

    @BeforeEach
    void setup() throws IOException {
        this.project = new PluginBuild(this.projectDir).settings();
    }

    @Test
    void userdevIsPublishedAsItsOwnModule() throws IOException {
        this.project.build("""
                group = 'com.cleanroommc'
                version = '0.1.0'
                cleanroom {
                    mode = 'loader'
                    patches.developInitial = false
                }
                gradle.projectsEvaluated {
                    def publication = publishing.publications.getByName('cleanroom')
                    assert publication.artifactId == 'cleanroom'
                    assert publication.version == '0.1.0'
                    def artifacts = publication.artifacts.collect {
                        (it.classifier ?: '') + '.' + it.extension
                    } as Set
                    assert artifacts == ['.zip', 'universal.jar', 'sources.jar',
                                         'javadoc.jar', 'installer.jar'] as Set : artifacts
                    def userdev = publishing.publications.getByName('cleanroomUserdev')
                    assert userdev.artifactId == 'cleanroom-userdev'
                    assert userdev.version == '0.1.0'
                    assert userdev.artifacts.count { it.classifier == 'sources' } == 1
                    // The stage is an artifact attribute, so the published variant does not carry one
                    assert configurations.cleanroomUserdevApiElements.attributes
                            .getAttribute(com.cleanroommc.gradle.api.userdev.UserdevAttributes.STAGE) == null
                    assert configurations.cleanroomUserdevApiElements.attributes
                            .getAttribute(com.cleanroommc.gradle.api.userdev.UserdevAttributes.ROLE) == 'classes'
                }
                """);

        var result = this.project.plainRunner("generatePomFileForCleanroomPublication",
                "generatePomFileForCleanroomUserdevPublication").build();
        assertEquals(TaskOutcome.SUCCESS, result.task(":generatePomFileForCleanroomPublication").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":generatePomFileForCleanroomUserdevPublication").getOutcome());

        var pom = Files.readString(this.projectDir.resolve("build/publications/cleanroom/pom-default.xml"));
        assertTrue(pom.contains("<groupId>com.cleanroommc</groupId>"), pom);
        assertTrue(pom.contains("<artifactId>cleanroom</artifactId>"), pom);
        assertTrue(pom.contains("<version>0.1.0</version>"), pom);
        var userdevPom = Files.readString(this.projectDir.resolve("build/publications/cleanroomUserdev/pom-default.xml"));
        assertTrue(userdevPom.contains("<artifactId>cleanroom-userdev</artifactId>"), userdevPom);
        assertTrue(userdevPom.contains("<artifactId>authlib</artifactId>"), userdevPom);

    }

    /**
     * A workspace resolves Minecraft's libraries and only its own machine's natives out of this metadata,
     * and every role reads the one raw archive rather than a classified copy of it.
     */
    @Test
    void userdevVariantsCarryLibrariesAndPerPlatformNatives() throws IOException {
        this.project.loader("""
                group = 'com.cleanroommc'
                version = '0.1.0'
                gradle.projectsEvaluated {
                    def roles = ['ApiElements', 'RuntimeElements', 'SourcesElements',
                                 'ClientExtraElements', 'ServerExtraElements']
                    def files = roles.collect { role ->
                        def artifacts = configurations.getByName('cleanroomUserdev' + role).outgoing.artifacts
                        assert artifacts.size() == 1 : role
                        def artifact = artifacts.iterator().next()
                        assert !artifact.classifier : role + ' publishes a second copy of the raw jar'
                        artifact.file
                    } as Set
                    assert files.size() == 1 : files

                    ['ApiElements', 'RuntimeElements'].each { role ->
                        assert configurations.getByName('cleanroomUserdev' + role).extendsFrom
                                .any { it.name == 'cleanroomUserdevMinecraftLibraries' } : role
                    }
                    assert configurations.cleanroomUserdevSourcesElements.extendsFrom
                            .any { it.name == 'cleanroomUserdevMinecraftLibraries' }
                    // 1.12.2's manifest, minus the LWJGL 2 modules the distribution replaces
                    def libraries = configurations.cleanroomUserdevMinecraftLibraries.allDependencies
                    assert libraries.any { it.group == 'com.mojang' && it.name == 'authlib' } : libraries
                    assert libraries.every { it.group != 'org.lwjgl.lwjgl' } : libraries
                }
                """);

        var output = this.project.plainRunner("help", "--offline").build().getOutput();
        assertTrue(output.contains("BUILD SUCCESSFUL"), output);
    }

    /**
     * Each native classifier is its own variant, so a consumer resolves one platform's rather than all.
     */
    @Test
    void everyNativeClassifierIsItsOwnAttributedVariant() throws IOException {
        var checks = Platform.nativePlatforms().stream().map(platform -> """
                        assertVariant('%s', '%s', '%s')
                """.formatted(capitalized(platform.lwjglNativesClassifier()),
                        platform.operatingSystemFamily(), platform.machineArchitecture()))
                .collect(Collectors.joining());
        this.project.loader("""
                import org.gradle.nativeplatform.MachineArchitecture
                import org.gradle.nativeplatform.OperatingSystemFamily

                group = 'com.cleanroommc'
                version = '0.1.0'
                ext.assertVariant = { suffix, os, architecture ->
                    def variant = configurations.getByName('cleanroomUserdev' + suffix + 'Elements')
                    assert variant.attributes.getAttribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE).name == os
                    assert variant.attributes.getAttribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE).name == architecture
                    assert variant.outgoing.artifacts.isEmpty() : suffix + ' should carry dependencies only'
                }
                gradle.projectsEvaluated {
                """ + checks + """
                }
                """);

        var output = this.project.plainRunner("help", "--offline").build().getOutput();
        assertTrue(output.contains("BUILD SUCCESSFUL"), output);
    }

    private static String capitalized(String classifier) {
        return Arrays.stream(classifier.split("-")).map(StringUtils::capitalize).collect(Collectors.joining());
    }

    @Test
    void assembleBuildsTheInstallerAndThePack() throws IOException {
        this.project.loader("""
                group = 'com.cleanroommc'
                version = '0.1.0'
                apply plugin: 'maven-publish'
                publishing.repositories.maven {
                    url = layout.buildDirectory.dir('fixture-repository')
                }
                // The distribution graph is Mojang's real library list, which this test has no need to download
                gradle.projectsEvaluated {
                    ['vanilla', 'distributionLibraries', 'distributionNatives'].each { name ->
                        configurations.named(name) { withDependencies { it.clear() } }
                    }
                }
                """);

        var output = this.project.runner("assemble", "--dry-run").build().getOutput();
        PluginBuild.scheduled(output, "universalJar", "userdevJar", "sourcesJar", "javadocJar",
                "publishMmcPackZip", "installerJar");
    }

}
