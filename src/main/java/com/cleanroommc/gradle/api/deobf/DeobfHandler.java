/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.deobf;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;

/**
 * Backs the {@code deobf(...)} notation inside a {@code dependencies} block.
 */
public class DeobfHandler {

    private final DependencyHandler dependencies;
    private final ConfigurationContainer configurations;
    private final ObjectFactory objects;
    private final FileCollection transformInputs;
    private final Set<String> classifiedModules = new HashSet<>();
    private final Set<Dependency> fileDependencies = new HashSet<>();

    @Inject
    public DeobfHandler(DependencyHandler dependencies, ConfigurationContainer configurations, ObjectFactory objects, FileCollection transformInputs) {
        this.dependencies = dependencies;
        this.configurations = configurations;
        this.objects = objects;
        this.transformInputs = transformInputs;
    }

    public boolean isDeobfuscated(Dependency dependency) {
        return this.fileDependencies.contains(dependency) ||
                dependency instanceof ExternalModuleDependency module && DeobfAttributes.MCP.equals(module.getAttributes().getAttribute(DeobfAttributes.DEOBFUSCATED));
    }

    public Dependency call(Object notation) {
        var dependency = this.dependencies.create(notation);
        if (dependency instanceof FileCollectionDependency files) {
            return fileDependency(files);
        }
        if (!(dependency instanceof ExternalModuleDependency module)) {
            throw new InvalidUserDataException(
                    "deobf(...) only accepts external module and file notations, got " + notation + " which resolves to " + dependency.getClass().getSimpleName() + "."
            );
        }
        if (!module.getArtifacts().isEmpty()) {
            if (module.getArtifacts().size() != 1) {
                throw new InvalidUserDataException("deobf(...) accepts one jar artifact per dependency");
            }
            var artifact = module.getArtifacts().iterator().next();
            if (!"jar".equals(artifact.getExtension())) {
                throw new InvalidUserDataException("deobf(...) only accepts jar artifacts");
            }
            var classifier = artifact.getClassifier();
            module.getArtifacts().clear();
            if (classifier != null && !classifier.isEmpty()) {
                var capability = artifact.getName() + "-cleanroom-deobf-" + classifier;
                var coordinate = module.getGroup() + ":" + module.getName();
                if (this.classifiedModules.add(coordinate + ":" + capability)) {
                    this.dependencies
                            .getComponents()
                            .withModule(coordinate, ClassifiedDeobfRule.class, spec -> spec.params(artifact.getName(), classifier, capability));
                }
                module.capabilities(capabilities -> capabilities.requireCapability(module.getGroup() + ":" + capability));
            }
        }
        module.attributes(attributes -> attributes.attribute(DeobfAttributes.DEOBFUSCATED, DeobfAttributes.MCP));
        return module;
    }

    /**
     * A file dependency cannot carry attributes of its own, so its jars are renamed through a detached
     * configuration and handed back as the files that view resolves to.
     */
    private Dependency fileDependency(FileCollectionDependency files) {
        var renamed = this.configurations.detachedConfiguration(files)
                .getIncoming()
                .artifactView(view -> view.getAttributes().attribute(DeobfAttributes.DEOBFUSCATED, DeobfAttributes.MCP))
                .getFiles();
        var dependency = this.dependencies.create(this.objects.fileCollection().from(renamed).builtBy(this.transformInputs));
        this.fileDependencies.add(dependency);
        return dependency;
    }

}
