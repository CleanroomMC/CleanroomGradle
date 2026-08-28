package com.cleanroommc.gradle.api.deobf;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

/**
 * Backs the {@code deobf(...)} notation inside a {@code dependencies} block.
 */
public class DeobfHandler {

    private final DependencyHandler dependencies;
    private final ObjectFactory objects;

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
            throw new InvalidUserDataException("deobf(...) only accepts external module notations, got "
                    + notation + " which resolves to " + dependency.getClass().getSimpleName() + ".");
        }
        module.attributes(attributes -> attributes.attribute(DeobfAttributes.DEOBFUSCATED, DeobfAttributes.MCP));
        return module;
    }

}
