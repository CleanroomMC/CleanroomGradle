/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util.dist;

import org.gradle.api.GradleException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolvedLibrariesTest {

    @TempDir
    Path directory;

    @Test
    void carriesSelectedVersionAndActualRepositoryIntoLibraryMetadata() throws IOException {
        var repositoryDirectory = this.directory.resolve("authority");
        var artifact = repositoryDirectory.resolve("example/authority/probe/2.0/probe-2.0.jar");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, "resolved by authority");

        var project = ProjectBuilder.builder().withProjectDir(this.directory.toFile()).build();
        var repository = project.getRepositories().maven(repo -> {
            repo.setName("Authority");
            repo.setUrl(repositoryDirectory);
            repo.getMetadataSources().artifact();
        });
        var configuration = project.getConfigurations().detachedConfiguration(project.getDependencies().create("example.authority:probe:1.0"));
        configuration.getResolutionStrategy().eachDependency(details -> details.useVersion("2.0"));

        var libraries = ResolvedLibraries.artifacts(
                project.getObjects(),
                configuration.getIncoming().getArtifacts().getResolvedArtifacts(),
                configuration.getIncoming().getResolutionResult().getRootComponent(),
                project.provider(() -> Map.of(repository.getName(), repository.getUrl().toString()))
        );

        assertThat(libraries.get().size()).isEqualTo(1);
        var library = libraries.get().getFirst();
        assertThat(library.getCoordinate().get()).isEqualTo("example.authority:probe:2.0");
        assertThat(library.getRepositoryUrl().get()).isEqualTo(repository.getUrl().toString());
    }

    @Test
    void collectsExcludeRulesFromAConfigurationHierarchy() {
        var project = ProjectBuilder.builder().withProjectDir(this.directory.toFile()).build();
        var parent = project.getConfigurations().create("parent");
        parent.exclude(Map.of("group", "com.mojang"));
        var child = project.getConfigurations().create("child");
        child.extendsFrom(parent);
        child.exclude(Map.of("module", "icu4j-core-mojang"));

        assertThat(ResolvedLibraries.excludeRules(child)).isEqualTo(Set.of("com.mojang:*", "*:icu4j-core-mojang"));
    }

    @Test
    void appliesExactAndWildcardExcludeRulesWithSharedSemantics() {
        var patchy = Coordinate.parse("com.mojang:patchy:1.3.9");

        assertThat(ResolvedLibraries.isExcluded(patchy, Set.of("com.mojang:patchy"))).isTrue();
        assertThat(ResolvedLibraries.isExcluded(patchy, Set.of("com.mojang:*"))).isTrue();
        assertThat(ResolvedLibraries.isExcluded(patchy, Set.of("*:patchy"))).isTrue();
        assertThat(ResolvedLibraries.isExcluded(patchy, Set.of("com.ibm.icu:*"))).isFalse();
        assertThatThrownBy(() -> ResolvedLibraries.isExcluded(patchy, Set.of("com.mojang"))).isInstanceOf(GradleException.class);
    }

    @Test
    void mergeNativesPinsPlatformVariantsToSelectedVersions() {
        var merged = ResolvedLibraries.mergeNatives(
                java.util.List.of("com.mojang:patchy:1.3.9", "net.java.jinput:jinput:2.0.5"),
                java.util.List.of("com.mojang:patchy:natives-linux", "unknown:lib:natives-linux")
        );
        assertThat(merged).isEqualTo(java.util.List.of("com.mojang:patchy:1.3.9", "net.java.jinput:jinput:2.0.5", "com.mojang:patchy:1.3.9:natives-linux"));
    }

}
