package com.cleanroommc.gradle.api.util.lazy;

import com.cleanroommc.gradle.api.util.dist.LibraryJson;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import java.util.HashMap;
import java.util.Map;

/**
 * The project's group, version and Maven repository URLs, as of the end of evaluation.
 *
 * <p>A pipeline is registered when its mode is picked, which is usually before the buildscript sets any of
 * them, so anything that names them reads this rather than {@code Project.getGroup()} and friends.
 */
public final class ProjectCoordinates {

    private final Property<String> group;
    private final Property<String> version;
    private final MapProperty<String, String> repositoryUrls;

    public ProjectCoordinates(Project project) {
        var objects = project.getObjects();
        this.group = objects.property(String.class);
        this.version = objects.property(String.class);
        this.repositoryUrls = objects.mapProperty(String.class, String.class);
        readFrom(project);
        project.afterEvaluate(this::readFrom);
    }

    private void readFrom(Project project) {
        this.group.set(String.valueOf(project.getGroup()));
        this.version.set(String.valueOf(project.getVersion()));
        var urls = new HashMap<String, String>();
        project.getRepositories().withType(MavenArtifactRepository.class).forEach(repository ->
                urls.put(repository.getName(), LibraryJson.trailingSlash(repository.getUrl().toString())));
        this.repositoryUrls.set(urls);
    }

    public Provider<String> getGroup() {
        return this.group;
    }

    public Provider<String> getVersion() {
        return this.version;
    }

    public Provider<Map<String, String>> getRepositoryUrls() {
        return this.repositoryUrls;
    }

}
