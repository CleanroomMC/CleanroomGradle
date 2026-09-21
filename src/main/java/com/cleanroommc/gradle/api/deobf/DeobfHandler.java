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

import groovy.lang.Closure;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.model.ObjectFactory;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;

/**
 * Backs the {@code deobf(...)} notation inside a {@code dependencies} block.
 */
public class DeobfHandler {

    private final DependencyHandler dependencies;
    private final ObjectFactory objects;
    private final Set<String> classifiedModules = new HashSet<>();

    @Inject
    public DeobfHandler(DependencyHandler dependencies, ObjectFactory objects) {
        this.dependencies = dependencies;
        this.objects = objects;
    }

    public Dependency call(Object notation) {
        return call(notation, _ -> { });
    }

    public Dependency call(Object notation, Action<? super DeobfSpec> action) {
        var spec = this.objects.newInstance(DeobfSpec.class);
        spec.getSources().convention(false);
        action.execute(spec);
        if (spec.getSources().get()) {
            throw new InvalidUserDataException("deobf(...) { sources = true } is not implemented yet");
        }
        return dependency(notation);
    }

    public Dependency call(Object notation, Closure<?> closure) {
        return call(notation, spec -> {
            closure.setDelegate(spec);
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
            closure.call(spec);
        });
    }

    private ExternalModuleDependency dependency(Object notation) {
        var dependency = this.dependencies.create(notation);
        if (!(dependency instanceof ExternalModuleDependency module)) {
            throw new InvalidUserDataException(
                    "deobf(...) only accepts external module notations, got " + notation + " which resolves to " + dependency.getClass().getSimpleName() + "."
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

}
