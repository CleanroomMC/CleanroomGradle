package com.cleanroommc.gradle.api.util.dist;

import com.cleanroommc.gradle.api.util.Property;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

/**
 * Default maven repositories for CleanroomGradle environments.
 */
public enum Repository {

    CENTRAL("Maven Central", "https://repo.maven.apache.org/maven2/"),
    CLEANROOM("CleanroomMC", "https://maven.cleanroommc.com/", true, "com.cleanroommc", "top.outlands", "zone.rong"),
    FORGE("MinecraftForge", "https://maven.minecraftforge.net/", true, "net.minecraftforge", "de.oceanlabs.mcp"),
    MOJANG("Mojang", "https://libraries.minecraft.net/");

    public static void addTo(Project project) {
        RepositoryHandler repos = project.getRepositories();
        boolean local = Property.ENABLE_EXCLUSIVE_LOCAL_MAVENS.bool(project.getProviders());
        for (Repository repo : values()) {
            if (repo == CENTRAL) {
                repos.mavenCentral();
                continue;
            }
            if (repo.groups().length == 0) {
                repo.create(repos);
                continue;
            }
            repos.exclusiveContent(exclusive -> {
                if (local) {
                    exclusive.forRepository(repos::mavenLocal);
                }
                exclusive.forRepository(() -> repo.create(repos));
                exclusive.filter(content -> {
                    for (var group : repo.groups()) {
                        content.includeGroup(group);
                    }
                });
            });
        }
    }

    private final String id;
    private final String url;
    private final boolean hasZips;
    private final String[] groups;

    Repository(String id, String url, String... groups) {
        this(id, url, false, groups);
    }

    Repository(String id, String url, boolean hasZips, String... groups) {
        this.id = id;
        this.url = url;
        this.hasZips = hasZips;
        this.groups = groups;
    }

    public String id() {
        return id;
    }

    public String url() {
        return url;
    }

    public boolean hasZips() {
        return hasZips;
    }

    public String[] groups() {
        return groups;
    }

    @Override
    public String toString() {
        return id;
    }

    private MavenArtifactRepository create(RepositoryHandler repositories) {
        return repositories.maven(maven -> {
            maven.setName(this.id);
            maven.setUrl(this.url);
            if (this.hasZips) {
                maven.getMetadataSources().artifact();
            }
        });
    }

}
