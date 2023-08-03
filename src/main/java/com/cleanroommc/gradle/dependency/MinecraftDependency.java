package com.cleanroommc.gradle.dependency;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.util.Configurable;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.cleanroommc.gradle.dependency.Side.JOINED;

public class MinecraftDependency implements SelfResolvingDependency, FileCollectionDependency, Configurable<MinecraftDependency> {
/*
    public static MinecraftDependency parseFromMap(String version, Map<String, ?> configurationMap) {
        if (configurationMap.get("loader") instanceof String loader) {
            if (Loader.VANILLA != Loader.valueOf(loader.toUpperCase())) {
                if (configurationMap.get("loaderVersion") instanceof String loaderVersion) {
                    if (configurationMap.get("mappingProvider") instanceof String mappingProvider) {
                        // When the mapping is described, mapping_version must also be stated
                        if (configurationMap.get("mappingVersion") instanceof String mappingVersion) {
                            MinecraftDependency minecraftDependency = new MinecraftDependency(version, loader, loaderVersion, mappingProvider, mappingVersion);
                            if (configurationMap.get("side") instanceof String side) {
                                minecraftDependency.side = side;
                            }
                            return minecraftDependency;
                        }
                    }
                }
            }
        }
        return new MinecraftDependency(version, Loader.VANILLA.getValue());
    }
*/


    private Project project;

    protected ConfigurableFileCollection files;
    protected String version, loaderVersion, mappingVersion;
    protected Side side;
    protected Loader loader;
    protected Mapping mappingProvider;

    private MinecraftDependency(Project project, String version) {
        this.project = project;
        this.version = version;
        side = JOINED;
        mappingProvider = Mapping.MCP;
        files = project.getObjects().fileCollection();
    }

    public MinecraftDependency(Project project, String version, Loader loader) {
        this(project, version);
        this.loader = loader;
    }

    public MinecraftDependency(Project project, String version, String loader) {
        this(project, version);
        this.loader = Loader.parse(loader);
    }

    public MinecraftDependency(Project project, String version, Loader loader, String loaderVersion, Mapping mappingProvider, String mappingVersion) {
        this(project, version, loader);
        this.loaderVersion = loaderVersion;
        this.mappingProvider = mappingProvider;
        this.mappingVersion = mappingVersion;
    }

    public MinecraftDependency(Project project, String version, String loader, String loaderVersion, String mappingProvider, String mappingVersion) {
        this(project, version, loader);
        this.loaderVersion = loaderVersion;
        this.mappingProvider = Mapping.parse(mappingProvider);
        this.mappingVersion = mappingVersion;
    }

    public String getVanillaVersion() {
        return version;
    }

    public String getTaskDescription() {
        return loader.getValue() + "_" + this.version + "_" + this.loaderVersion + "_" + mappingProvider.getValue() + "_" + this.mappingVersion;
    }

    @Override
    public String getGroup() {
        return createModuleIdentifier().getGroup();
    }

    @Override
    public @NotNull String getName() {
        return createModuleIdentifier().getName();
    }

    @Nullable
    @Override
    public String getVersion() {
        return createDefaultModuleVersionIdentifier().getVersion();
    }

    @Override
    public boolean contentEquals(Dependency dependency) {
        return this.equals(dependency);
    }

    @Override
    public @NotNull MinecraftDependency copy() {
        return new MinecraftDependency(this.project, this.version, this.loader, this.loaderVersion, this.mappingProvider, this.mappingVersion);
    }

    @Nullable
    @Override
    public String getReason() {
        return null;
    }

    @Override
    public void because(@org.jetbrains.annotations.Nullable String reason) {

    }

    @Override
    public int hashCode() {
        return Objects.hash(this.version, this.loader, this.loaderVersion, this.mappingProvider, this.mappingVersion);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MinecraftDependency o) {
            return Objects.equals(this.version, o.version) && Objects.equals(this.loader, o.loader) && Objects.equals(this.loaderVersion, o.loaderVersion) &&
                    Objects.equals(this.mappingProvider, o.mappingProvider) && Objects.equals(this.mappingVersion, o.mappingVersion);
        }
        return false;
    }

    @Override
    public String toString() {
        if (Loader.VANILLA == this.loader) {
            return String.format("Vanilla Minecraft Dependency: { Version: %s }", this.version);
        } else {
            return String.format("%s Minecraft Dependency: { Version: %s | Loader Version: %s | Mapping: %s@%s }",
                    StringUtils.capitalize(this.loader.getValue()), this.version, this.loaderVersion, this.mappingProvider, this.mappingVersion);
        }
    }

    @Override
    public MinecraftDependency configure(@DelegatesTo(MinecraftDependency.class) Closure closure) {
        closure.call(this);
        return this;
    }

    private ModuleIdentifier createModuleIdentifier() {
        return DefaultModuleIdentifier.newId("cleanroom_internal", this.loader.getValue());
    }

    private ModuleVersionIdentifier createDefaultModuleVersionIdentifier() {
        return DefaultModuleVersionIdentifier.newId(createModuleIdentifier(), this.version + (this.loader == null ? "" : "@" + this.loaderVersion));
    }

    public Side getSide() {
        return side;
    }

    public Loader getLoader() {
        return loader;
    }

    public Mapping getMappingProvider() {
        return mappingProvider;
    }

    @Override
    public Set<File> resolve() {
        return files.getFiles();
    }

    @Override
    public FileCollection getFiles() {
        return files;
    }

    @Override
    public Set<File> resolve(boolean transitive) {
        return resolve();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return files.getBuildDependencies();
    }

    public void addFiles(List<File> files) {
        this.files.setFrom(files);
    }
}
