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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        var configuration = project.getConfigurations().detachedConfiguration(
                project.getDependencies().create("example.authority:probe:1.0"));
        configuration.getResolutionStrategy().eachDependency(details -> details.useVersion("2.0"));

        var libraries = ResolvedLibraries.artifacts(project.getObjects(),
                configuration.getIncoming().getArtifacts().getResolvedArtifacts(),
                configuration.getIncoming().getResolutionResult().getRootComponent(),
                project.provider(() -> Map.of(repository.getName(), repository.getUrl().toString())));

        assertEquals(1, libraries.get().size());
        var library = libraries.get().getFirst();
        assertEquals("example.authority:probe:2.0", library.getCoordinate().get());
        assertEquals(repository.getUrl().toString(), library.getRepositoryUrl().get());
    }

    @Test
    void collectsExcludeRulesFromAConfigurationHierarchy() {
        var project = ProjectBuilder.builder().withProjectDir(this.directory.toFile()).build();
        var parent = project.getConfigurations().create("parent");
        parent.exclude(Map.of("group", "com.mojang"));
        var child = project.getConfigurations().create("child");
        child.extendsFrom(parent);
        child.exclude(Map.of("module", "icu4j-core-mojang"));

        assertEquals(Set.of("com.mojang:*", "*:icu4j-core-mojang"),
                ResolvedLibraries.excludeRules(child));
    }

    @Test
    void appliesExactAndWildcardExcludeRulesWithSharedSemantics() {
        var patchy = Coordinate.parse("com.mojang:patchy:1.3.9");

        assertTrue(ResolvedLibraries.isExcluded(patchy, Set.of("com.mojang:patchy")));
        assertTrue(ResolvedLibraries.isExcluded(patchy, Set.of("com.mojang:*")));
        assertTrue(ResolvedLibraries.isExcluded(patchy, Set.of("*:patchy")));
        assertFalse(ResolvedLibraries.isExcluded(patchy, Set.of("com.ibm.icu:*")));
        assertThrows(GradleException.class,
                () -> ResolvedLibraries.isExcluded(patchy, Set.of("com.mojang")));
    }

}
