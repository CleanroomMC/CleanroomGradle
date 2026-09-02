package com.cleanroommc.gradle.api.userdev;

import com.cleanroommc.gradle.api.schema.UserdevConfig;
import com.cleanroommc.gradle.api.source.UserdevConfigValueSource;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;

import java.util.Objects;

/**
 * The dependency and artifact-owned metadata registered by {@code cleanroom.userdev(...)}.
 */
public final class UserdevDependency implements ProviderConvertible<ExternalModuleDependency> {

    private final ExternalModuleDependency dependency;
    private final Provider<ExternalModuleDependency> dependencyProvider;
    private final ConfigurableFileCollection accessTransformers;
    private final Provider<RegularFile> rawArtifact;
    private final Provider<UserdevConfig> config;
    private final Configuration rawConfiguration;

    public UserdevDependency(Project project, String version) {
        Objects.requireNonNull(version, "version");
        this.dependency = (ExternalModuleDependency) project.getDependencies()
                .create("com.cleanroommc:cleanroom-userdev:" + version);
        this.dependencyProvider = project.provider(() -> this.dependency);
        this.dependency.attributes(attributes -> {
            attributes.attribute(UserdevAttributes.STAGE, UserdevAttributes.MATERIALIZED);
        });
        this.accessTransformers = project.getObjects().fileCollection();

        var rawDependency = this.dependency.copy();
        rawDependency.attributes(attributes -> {
            attributes.attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW);
            attributes.attribute(UserdevAttributes.ROLE, UserdevAttributes.CLASSES);
        });
        Configuration metadata = project.getConfigurations().detachedConfiguration(rawDependency);
        metadata.setTransitive(false);
        metadata.attributes(attributes -> {
            attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
            attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
            attributes.attribute(UserdevAttributes.STAGE, UserdevAttributes.RAW);
            attributes.attribute(UserdevAttributes.ROLE, UserdevAttributes.CLASSES);
        });
        this.rawArtifact = metadata.getIncoming().getArtifacts().getResolvedArtifacts().map(artifacts -> {
            if (artifacts.size() != 1) {
                throw new IllegalStateException("Expected one Cleanroom userdev artifact, resolved " + artifacts.size() + ".");
            }
            return project.getLayout().file(project.provider(() -> artifacts.iterator().next().getFile())).get();
        });
        this.config = this.rawArtifact.flatMap(artifact -> project.getProviders().of(UserdevConfigValueSource.class,
                spec -> spec.getParameters().getUserdevJar().set(artifact)));
        this.rawConfiguration = metadata;
    }

    @Override
    public Provider<ExternalModuleDependency> asProvider() {
        return this.dependencyProvider;
    }

    public ExternalModuleDependency getModuleDependency() {
        return this.dependency;
    }

    public ConfigurableFileCollection getAccessTransformers() {
        return this.accessTransformers;
    }

    public Provider<RegularFile> getRawArtifact() {
        return this.rawArtifact;
    }

    public Provider<UserdevConfig> getConfig() {
        return this.config;
    }

    public Provider<String> getMinecraftVersion() {
        return this.config.map(UserdevConfig::minecraftVersion);
    }

    public Provider<String> getLoaderVersion() {
        return this.config.map(UserdevConfig::loaderVersion);
    }

    public Provider<String> getMcpToSrgPath() {
        return this.config.map(config -> config.layout().mcpToSrg());
    }

    public Provider<String> getSrgToMcpPath() {
        return this.config.map(config -> config.layout().srgToMcp());
    }

    public Configuration getRawConfiguration() {
        return this.rawConfiguration;
    }

}
