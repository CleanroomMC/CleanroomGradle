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

import com.cleanroommc.gradle.api.util.Property;

import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.file.Directory;

/**
 * Default maven repositories for CleanroomGradle environments.
 */
public enum Repository {

    CENTRAL("Maven Central", "https://repo.maven.apache.org/maven2/"),
    CLEANROOM("CleanroomMC", "https://maven.cleanroommc.com/", true, "com.cleanroommc", "top.outlands", "zone.rong",
        "net.minecraftforge", "de.oceanlabs.mcp"
    ),
    MOJANG("Mojang", "https://libraries.minecraft.net/");

    /** Resolves the userdev artifact as published, past the sources mirror. */
    public static final String USERDEV_RAW_CONFIGURATION = "_cleanroomUserdevRaw";
    /** Builds the sources the mirror is filled with, so it has to resolve past the mirror too. */
    public static final String USERDEV_SOURCES_CONFIGURATION = "_cleanroomUserdevSources";

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

    // A project-local copy of the userdev module that adds the sources built on this machine
    public static Directory userdevSourcesMirror(Project project) {
        return project.getLayout().getProjectDirectory().dir(".gradle/cleanroom-userdev-sources");
    }

    public static void addTo(Project project) {
        RepositoryHandler repos = project.getRepositories();
        boolean local = Property.ENABLE_EXCLUSIVE_LOCAL_MAVENS.bool(project.getProviders()).getOrElse(Boolean.FALSE);
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
                if (repo == CLEANROOM) {
                    // IDEs fetch sources by classifier from the repository a module came from
                    exclusive.forRepository(() -> repos.maven(maven -> {
                        maven.setName("CleanroomUserdevSources");
                        maven.setUrl(userdevSourcesMirror(project));
                        maven.getMetadataSources().gradleMetadata();
                        maven.content(content -> content.notForConfigurations(USERDEV_RAW_CONFIGURATION,
                            USERDEV_SOURCES_CONFIGURATION
                        ));
                    }));
                }
                if (local) {
                    exclusive.forRepository(() -> repos.mavenLocal(maven -> {
                        maven.getMetadataSources().gradleMetadata(); // Userdev
                        maven.getMetadataSources().mavenPom();
                        maven.getMetadataSources().artifact();
                    }));
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
