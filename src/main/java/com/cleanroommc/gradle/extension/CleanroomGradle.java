package com.cleanroommc.gradle.extension;

import com.cleanroommc.gradle.dependency.Loader;
import com.cleanroommc.gradle.dependency.Mapping;
import com.cleanroommc.gradle.dependency.MinecraftDependency;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

/**
 * Helper class for buildscripts to utilize. This is injected in as an extension under the name 'cg'
 */
public final class CleanroomGradle {

    public static final String EXTENSION_NAME = "cg";

    //Version registered to this project instance
    private final Set<String> versions = new HashSet<>();

    private final Set<MinecraftDependency> minecraftDependencies = new HashSet<>();

    private final Project project;

    public CleanroomGradle(Project project) {
        this.project = project;
    }

    @Unmodifiable
    public Set<String> getVersions() {
        return Collections.unmodifiableSet(versions);
    }

    @Unmodifiable
    public Set<MinecraftDependency> getMinecraftDependencies() {
        return Collections.unmodifiableSet(minecraftDependencies);
    }


    /* Dependency-related Methods */

    public MinecraftDependency vanilla(String version) {
        return minecraft(version);
    }

    public MinecraftDependency forge(String version, String loaderVersion, String mappingVersion) {
        return minecraft(version, Loader.FORGE.getValue(), loaderVersion, Mapping.MCP.getValue(), mappingVersion);
    }

    public MinecraftDependency forge(String version, String loaderVersion, String mappingVersion, Action<MinecraftDependency> configurationAction) {
        MinecraftDependency minecraftDependency = minecraft(version, Loader.FORGE.getValue(), loaderVersion, Mapping.MCP.getValue(), mappingVersion);
        configurationAction.execute(minecraftDependency);
        return minecraftDependency;
    }

    public MinecraftDependency cleanroom(String cleanroomVersion) {
        return minecraft("1.12.2", Loader.CLEANROOM.getValue(), cleanroomVersion, Mapping.MCP.getValue(), "stable_39");
    }

    public MinecraftDependency cleanroom(String cleanroomVersion, Action<MinecraftDependency> configurationAction) {
        MinecraftDependency minecraftDependency = minecraft("1.12.2", Loader.CLEANROOM.getValue(), cleanroomVersion, Mapping.MCP.getValue(), "stable_39");
        configurationAction.execute(minecraftDependency);
        return minecraftDependency;
    }

    public MinecraftDependency minecraft(String version) {
        return registerDep(new MinecraftDependency(project, version, Loader.VANILLA.getValue()));
    }

    public MinecraftDependency minecraft(String version, Action<MinecraftDependency> configurationAction) {
        MinecraftDependency minecraftDependency = minecraft(version);
        configurationAction.execute(minecraftDependency);
        return minecraftDependency;
    }

/*
    public MinecraftDependency minecraft(String version, Map<String, ?> configurationMap) {
        return registerDep(MinecraftDependency.parseFromMap(version, configurationMap));
    }
*/

    public MinecraftDependency minecraft(String version, String loader, String loaderVersion, String mappingProvider, String mappingVersion) {
        return registerDep(new MinecraftDependency(project, version, loader, loaderVersion, mappingProvider, mappingVersion));
    }

    private MinecraftDependency registerDep(MinecraftDependency dep) {
        versions.add(dep.getVanillaVersion());
        minecraftDependencies.add(dep);
        return dep;
    }
}
