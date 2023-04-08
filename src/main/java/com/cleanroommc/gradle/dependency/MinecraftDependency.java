package com.cleanroommc.gradle.dependency;

import groovy.lang.Closure;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.dependencies.AbstractModuleDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint;
import org.gradle.util.Configurable;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class MinecraftDependency extends AbstractModuleDependency implements ExternalModuleDependency, Configurable<MinecraftDependency> {

    public static final String VANILLA = "vanilla";
    public static final String FORGE = "forge";
    public static final String CLEANROOM = "cleanroom";

    public static final String MCP = "mcp";

    public static final String CLIENT_ONLY = "client";
    public static final String SERVER_ONLY = "server";
    public static final String JOINED = "joined";

    public static MinecraftDependency parseFromMap(String version, Map<String, ?> configurationMap) {
        if (configurationMap.get("loader") instanceof String loader) {
            if (!VANILLA.equals(loader)) {
                if (configurationMap.get("loaderVersion") instanceof String loaderVersion) {
                    if (configurationMap.get("mappingProvider") instanceof String mappingProvider) {
                        // When the mapping is described, mapping_version must also be stated
                        if (configurationMap.get("mappingVersion") instanceof String mappingVersion) {
                            MinecraftDependency minecraftDependency = new MinecraftDependency(version, loader, loaderVersion, mappingProvider, mappingVersion);
                            if (configurationMap.get("side") instanceof String side) {
                                minecraftDependency.side = side;
                                minecraftDependency.checkSideConstraints();
                            }
                            return minecraftDependency;
                        }
                    }
                }
            }
        }
        return new MinecraftDependency(version, VANILLA);
    }

    // Temporary
    public static Set<String> getUniqueVanillaVersions(Set<MinecraftDependency> minecraftDependencies) {
        return minecraftDependencies.stream().map(MinecraftDependency::getVanillaVersion).collect(Collectors.toSet());
    }

    public static Set<MinecraftDependency> getMinecraftDependencies(Project project) {
        return project.getConfigurations().stream()
                .map(Configuration::getDependencies)
                .flatMap(Collection::stream)
                .filter(MinecraftDependency.class::isInstance)
                .map(MinecraftDependency.class::cast)
                .collect(Collectors.toSet());
    }

    protected String version, loader, loaderVersion, mappingProvider, mappingVersion;
    protected String side = JOINED;

    public MinecraftDependency(String version, String loader) {
        super(null);
        this.version = version;
        this.loader = loader;
        checkLoaderConstraints();
    }

    public MinecraftDependency(String version, String loader, String loaderVersion, String mappingProvider, String mappingVersion) {
        this(version, loader);
        this.loaderVersion = loaderVersion;
        this.mappingProvider = mappingProvider;
        this.mappingVersion = mappingVersion;
        checkMappingConstraints();
    }

    public String getVanillaVersion() {
        return version;
    }

    @Override
    public boolean isChanging() {
        return false;
    }

    @Override
    public ExternalModuleDependency setChanging(boolean changing) {
        return copy();
    }

    @Override
    public boolean isForce() {
        return false;
    }

    @Override
    public String getGroup() {
        return createModuleIdentifier().getGroup();
    }

    @Override
    public String getName() {
        return createModuleIdentifier().getName();
    }

    @Nullable
    @Override
    public String getVersion() {
        return createDefaultModuleVersionIdentifier().getVersion();
    }

    @Override
    public boolean matchesStrictly(ModuleVersionIdentifier identifier) {
        return identifier.equals(createDefaultModuleVersionIdentifier());
    }

    @Override
    public ModuleIdentifier getModule() {
        return createModuleIdentifier();
    }

    @Override
    public boolean contentEquals(Dependency dependency) {
        return this.equals(dependency);
    }

    @Override
    public MinecraftDependency copy() {
        return new MinecraftDependency(this.version, this.loader, this.loaderVersion, this.mappingProvider, this.mappingVersion);
    }

    @Override
    public void version(Action<? super MutableVersionConstraint> configureAction) {
        throw new UnsupportedOperationException("Cannot configure mutable version constraints");
    }

    @Override
    public VersionConstraint getVersionConstraint() {
        return createPlaceholderVersionConstraint();
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
        if ("vanilla".equals(this.loader)) {
            return String.format("Vanilla Minecraft Dependency: { Version: %s }", this.version);
        } else {
            return String.format("%s Minecraft Dependency: { Version: %s | Loader Version: %s | Mapping: %s@%s }",
                    StringUtils.capitalize(this.loader), this.version, this.loaderVersion, this.mappingProvider, this.mappingVersion);
        }
    }

    @Override
    public MinecraftDependency configure(Closure closure) {
        closure.call(this);
        checkLoaderConstraints();
        checkMappingConstraints();
        checkSideConstraints();
        return this;
    }

    private ModuleIdentifier createModuleIdentifier() {
        return DefaultModuleIdentifier.newId("cleanroom_internal", this.loader);
    }

    private VersionConstraint createPlaceholderVersionConstraint() {
        return new DefaultMutableVersionConstraint(this.version);
    }

    private ModuleVersionIdentifier createDefaultModuleVersionIdentifier() {
        return DefaultModuleVersionIdentifier.newId(createModuleIdentifier(), this.version + (this.loader == null ? "" : "@" + this.loaderVersion));
    }

    private void checkLoaderConstraints() {
        if (VANILLA.equals(this.loader) || FORGE.equals(this.loader) || CLEANROOM.equals(this.loader)) {
            return;
        }
        throw new UnsupportedOperationException(String.format("%s loader not supported!", this.loader));
    }

    private void checkMappingConstraints() {
        if (MCP.equals(this.mappingProvider)) {
            return;
        }
        throw new UnsupportedOperationException(String.format("%s mapping provider not supported!", this.mappingProvider));
    }

    private void checkSideConstraints() {
        if (CLIENT_ONLY.equals(this.side) || SERVER_ONLY.equals(this.side) || JOINED.equals(this.side)) {
            return;
        }
        throw new UnsupportedOperationException(String.format("%s side not supported!", this.side));
    }


}
