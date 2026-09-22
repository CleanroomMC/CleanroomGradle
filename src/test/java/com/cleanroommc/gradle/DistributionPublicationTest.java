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

import com.cleanroommc.gradle.api.util.Platform;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import org.gradle.testkit.runner.TaskOutcome;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The installer and the MMC pack have to travel with the rest of the distribution, and a userdev workspace
 * has to be able to ask the published module for {@code :userdev} and {@code :sources}.
 */
class DistributionPublicationTest extends BaseFunctionalTest {

    @Test
    void userdevIsPublishedAsItsOwnModule() throws IOException {
        this.project.build(
                """
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
                """
        );

        var result = this.project.plainRunner("generatePomFileForCleanroomPublication", "generatePomFileForCleanroomUserdevPublication").build();
        assertThat(result.task(":generatePomFileForCleanroomPublication").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":generatePomFileForCleanroomUserdevPublication").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        var pom = Files.readString(this.projectDir.resolve("build/publications/cleanroom/pom-default.xml"));
        assertThat(pom).as(pom).contains("<groupId>com.cleanroommc</groupId>");
        assertThat(pom).as(pom).contains("<artifactId>cleanroom</artifactId>");
        assertThat(pom).as(pom).contains("<version>0.1.0</version>");
        var userdevPom = Files.readString(this.projectDir.resolve("build/publications/cleanroomUserdev/pom-default.xml"));
        assertThat(userdevPom).as(userdevPom).contains("<artifactId>cleanroom-userdev</artifactId>");
        assertThat(userdevPom).as(userdevPom).contains("<artifactId>authlib</artifactId>");

    }

    /**
     * A workspace resolves Minecraft's libraries and only its own machine's natives out of this metadata.
     * Every role reads the one raw archive rather than a classified copy of it, and each native classifier
     * is its own attributed variant so a consumer resolves one platform's rather than all.
     */
    @Test
    void userdevVariantsCarryLibrariesAndPerPlatformNatives() throws IOException {
        var natives = Platform.nativePlatforms()
                .stream()
                .map(platform -> "assertNatives('%s', '%s', '%s')\n".formatted(
                        capitalized(platform.lwjglNativesClassifier()),
                        platform.operatingSystemFamily(),
                        platform.machineArchitecture()
                ))
                .collect(Collectors.joining());
        this.project.loader(
                """
                import org.gradle.nativeplatform.MachineArchitecture
                import org.gradle.nativeplatform.OperatingSystemFamily
                group = 'com.cleanroommc'
                version = '0.1.0'
                ext.assertNatives = { suffix, os, architecture ->
                    def variant = configurations.getByName('cleanroomUserdev' + suffix + 'Elements')
                    assert variant.attributes.getAttribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE).name == os
                    assert variant.attributes.getAttribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE).name == architecture
                    assert variant.outgoing.artifacts.isEmpty() : suffix + ' should carry dependencies only'
                }
                gradle.projectsEvaluated {
                    def roles = ['ApiElements', 'RuntimeElements', 'SourcesElements', 'ClientExtraElements', 'ServerExtraElements']
                    def files = roles.collect { role ->
                        def artifacts = configurations.getByName('cleanroomUserdev' + role).outgoing.artifacts
                        assert artifacts.size() == 1 : role
                        assert !artifacts.first().classifier : role + ' publishes a second copy of the raw jar'
                        artifacts.first().file
                    } as Set
                    assert files.size() == 1 : files
                    ['ApiElements', 'RuntimeElements', 'SourcesElements'].each { role ->
                        assert configurations.getByName('cleanroomUserdev' + role).extendsFrom
                                .any { it.name == 'cleanroomUserdevMinecraftLibraries' } : role
                    }
                    // 1.12.2's manifest, minus the LWJGL 2 modules the distribution replaces
                    def libraries = configurations.cleanroomUserdevMinecraftLibraries.allDependencies
                    assert libraries.any { it.group == 'com.mojang' && it.name == 'authlib' } : libraries
                    assert libraries.every { it.group != 'org.lwjgl.lwjgl' } : libraries
                """ +
                        natives + """
                }
                """
        );

        this.project.plainRunner("help", "--offline").build();
    }

    /**
     * A platform on the compile classpath cannot version the natives variants, and a consumer that
     * resolves a coordinate without one gets an unresolvable dependency rather than a build failure here.
     */
    @Test
    void nativesWithoutAVersionAreRejected() throws IOException {
        this.project.loader(
                """
                group = 'com.cleanroommc'
                version = '0.1.0'
                dependencies {
                    lwjglNative 'org.lwjgl:lwjgl'
                }
                // The natives sets are built on demand, the same way publishing realizes them
                tasks.register('realizeNatives') {
                    def natives = configurations.cleanroomUserdevNativesLinuxElements
                    doLast { natives.allDependencies.toList() }
                }
                """
        );

        var output = this.project.plainRunner("realizeNatives", "--offline").buildAndFail().getOutput();
        assertThat(output).as(output).contains("org.lwjgl:lwjgl is declared in lwjglNative without a version");
    }

    private static String capitalized(String classifier) {
        return Arrays.stream(classifier.split("-")).map(StringUtils::capitalize).collect(Collectors.joining());
    }

    @Test
    void assembleBuildsTheInstallerAndThePack() throws IOException {
        this.project.loader(
                """
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
                """
        );

        var output = this.project.runner("assemble", "--dry-run").build().getOutput();
        PluginBuild.scheduled(output, "universalJar", "userdevJar", "sourcesJar", "javadocJar", "publishMmcPackZip", "installerJar");
    }

    @Test
    void packEmbedsTheUniversalJarUnlessTheBuildUploadsIt() throws IOException {
        this.project.loader(
                """
                group = 'com.cleanroommc'
                version = '0.1.0'
                apply plugin: 'maven-publish'
                publishing.repositories.maven {
                    url = 'https://maven.example.invalid/'
                }
                gradle.projectsEvaluated {
                    ['vanilla', 'distributionLibraries', 'distributionNatives'].each { name ->
                        configurations.named(name) { withDependencies { it.clear() } }
                    }
                    gradle.taskGraph.whenReady {
                        println 'embedUniversalJar=' + tasks.named('publishMmcPackZip').get().embedUniversalJar.get()
                    }
                }
                """
        );

        assertThat(this.project.runner("publishMmcPackZip", "--dry-run").build().getOutput()).contains("embedUniversalJar=true");
        assertThat(this.project.runner("publishToMavenLocal", "--dry-run").build().getOutput()).contains("embedUniversalJar=true");
        assertThat(this.project.runner("publishCleanroomPublicationToMavenRepository", "--dry-run").build().getOutput()).contains("embedUniversalJar=false");
    }

    @Test
    void distributionsBuildAndCarryProjectDependencies() throws IOException {
        Files.writeString(this.projectDir.resolve("settings.gradle"), "include 'lib'\n", StandardOpenOption.APPEND);
        Files.createDirectories(this.projectDir.resolve("lib"));
        Files.writeString(this.projectDir.resolve("lib/build.gradle"), "plugins { id 'java-library' }\ngroup = 'com.example'\nversion = '1.2'\n");
        this.project.loader(
                """
                group = 'com.cleanroommc'
                version = '0.1.0'
                apply plugin: 'maven-publish'
                publishing.repositories.maven {
                    url = 'https://maven.example.invalid/'
                }
                dependencies {
                    implementation project(':lib')
                }
                gradle.projectsEvaluated {
                    ['vanilla', 'distributionLibraries', 'distributionNatives'].each { name ->
                        configurations.named(name) { withDependencies { it.clear() } }
                    }
                }
                """
        );

        var output = this.project.runner("publishMmcPackZip", "writeInstallProfile", "--dry-run").build().getOutput();
        PluginBuild.scheduled(output, "lib:jar", "publishMmcPackZip", "writeInstallProfile");
    }

}
