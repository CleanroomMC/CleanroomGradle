package com.cleanroommc.gradle.dependency;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
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

import static com.cleanroommc.gradle.dependency.Side.JOINED;

public class MinecraftDependency extends AbstractModuleDependency implements ExternalModuleDependency, Configurable<MinecraftDependency> {

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
    protected String side = JOINED.getValue();
    private Side internalSide;
    private Loader internalLoader;
    private Mapping internalMappingProvider;

    public MinecraftDependency(String version, String loader) {
        super(null);
        this.version = version;
        this.loader = loader;
        validateDep();
    }

    public MinecraftDependency(String version, String loader, String loaderVersion, String mappingProvider, String mappingVersion) {
        this(version, loader);
        this.loaderVersion = loaderVersion;
        this.mappingProvider = mappingProvider;
        this.mappingVersion = mappingVersion;
    }

    public String getVanillaVersion() {
        return version;
    }

    public String getTaskDescription() {
        return internalLoader.getValue() + "_" + this.version + "_" + this.loaderVersion + "_" + internalMappingProvider.getValue() + "_" + this.mappingVersion;
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
        if (Loader.VANILLA == this.internalLoader) {
            return String.format("Vanilla Minecraft Dependency: { Version: %s }", this.version);
        } else {
            return String.format("%s Minecraft Dependency: { Version: %s | Loader Version: %s | Mapping: %s@%s }",
                    StringUtils.capitalize(this.internalLoader.getValue()), this.version, this.loaderVersion, this.mappingProvider, this.mappingVersion);
        }
    }

    @Override
    public MinecraftDependency configure(@DelegatesTo(MinecraftDependency.class) Closure closure) {
        closure.call(this);
        return this;
    }

    private ModuleIdentifier createModuleIdentifier() {
        return DefaultModuleIdentifier.newId("cleanroom_internal", this.internalLoader.getValue());
    }

    private VersionConstraint createPlaceholderVersionConstraint() {
        return new DefaultMutableVersionConstraint(this.version);
    }

    private ModuleVersionIdentifier createDefaultModuleVersionIdentifier() {
        return DefaultModuleVersionIdentifier.newId(createModuleIdentifier(), this.version + (this.loader == null ? "" : "@" + this.loaderVersion));
    }

    private void validateDep() {
        parseLoader();
        parseSide();
        parseMapping();
    }

    private void parseLoader() {
        try {
            internalLoader = Loader.valueOf(loader.toUpperCase());
        } catch (IllegalArgumentException e) {
            UnsupportedOperationException exception = new UnsupportedOperationException(String.format("%s loader not supported!", loader));
            exception.addSuppressed(e);
            throw exception;
        }
    }

    private void parseSide() {
        try {
            internalSide = Side.valueOf(side.toUpperCase());
        } catch (IllegalArgumentException e) {
            UnsupportedOperationException exception = new UnsupportedOperationException(String.format("%s side not supported!", side));
            exception.addSuppressed(e);
            throw exception;
        }
    }

    private void parseMapping() {
        if (mappingProvider == null) {
            internalMappingProvider = Mapping.MCP;
            return;
        }
        try {
            internalMappingProvider = Mapping.valueOf(mappingProvider.toUpperCase());
        } catch (IllegalArgumentException e) {
            UnsupportedOperationException exception = new UnsupportedOperationException(String.format("%s mapping provider not supported!", mappingProvider));
            exception.addSuppressed(e);
            throw exception;
        }
    }

    public Side getInternalSide() {
        return internalSide;
    }

    public Loader getInternalLoader() {
        return internalLoader;
    }

    public Mapping getInternalMappingProvider() {
        return internalMappingProvider;
    }
}
