package com.cleanroommc.gradle.api.util.dist;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                Map.of(repository.getName(), repository.getUrl().toString()));

        assertEquals(1, libraries.get().size());
        var library = libraries.get().getFirst();
        assertEquals("example.authority:probe:2.0", library.getCoordinate().get());
        assertEquals(repository.getUrl().toString(), library.getRepositoryUrl().get());
    }

}
