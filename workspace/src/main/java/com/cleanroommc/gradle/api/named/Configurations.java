package com.cleanroommc.gradle.api.named;

import org.gradle.api.Project;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.Configuration;

import java.util.Collection;

// Configuration is not lazy at this moment in time for gradle, hence we resolve it straight away
public final class Configurations {

    public static Collection<Configuration> all(Project project) {
        return project.getConfigurations();
    }

    public static Configuration of(Project project, String name, boolean transitive) {
        var configuration = of(project, name);
        configuration.setTransitive(transitive);
        return configuration;
    }

    public static Configuration of(Project project, String name) {
        try {
            return project.getConfigurations().named(name).get();
        } catch (UnknownDomainObjectException ignored) { }
        return project.getConfigurations().register(name).get();
    }

    private Configurations() { }

}
