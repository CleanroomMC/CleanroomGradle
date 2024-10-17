package com.cleanroommc.gradle.api.named.dependency;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;

public final class Dependencies {

    public static ModuleDependency add(Project project, NamedDomainObjectProvider<Configuration> configuration, String notation) {
        return (ModuleDependency) project.getDependencies().add(configuration.getName(), notation);
    }

    private Dependencies() { }

}
