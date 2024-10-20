package com.cleanroommc.gradle.api.named;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.Configuration;

import java.util.Collection;

public final class Configurations {

    public static Collection<Configuration> all(Project project) {
        return project.getConfigurations();
    }

    public static NamedDomainObjectProvider<Configuration> of(Project project, String name, boolean transitive) {
        var configuration = of(project, name);
        configuration.configure(c -> c.setTransitive(transitive));
        return configuration;
    }

    public static NamedDomainObjectProvider<Configuration> of(Project project, String name) {
        try {
            return project.getConfigurations().named(name);
        } catch (UnknownDomainObjectException ignored) { }
        return project.getConfigurations().register(name);
    }

    private Configurations() { }

}
