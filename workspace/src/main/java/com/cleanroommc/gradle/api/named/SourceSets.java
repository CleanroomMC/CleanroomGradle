package com.cleanroommc.gradle.api.named;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import java.io.File;

public final class SourceSets {

    public static SourceSetContainer container(Project project) {
        return project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
    }

    public static SourceSet main(Project project) {
        return container(project).getAt("main");
    }

    public static NamedDomainObjectProvider<SourceSet> getOrCreate(Project project, String name) {
        try {
            return container(project).named(name);
        } catch (UnknownDomainObjectException ignored) { }
        return container(project).register(name);
    }

    public static Provider<File> sourceFrom(NamedDomainObjectProvider<SourceSet> sourceSet) {
        return sourceSet.map(SourceSets::sourceFrom);
    }

    public static File sourceFrom(SourceSet sourceSet) {
        return sourceSet.getAllJava().getSrcDirs().iterator().next();
    }

    private SourceSets() { }

}
