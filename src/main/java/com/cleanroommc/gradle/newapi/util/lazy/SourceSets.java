package com.cleanroommc.gradle.newapi.util.lazy;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;

import java.io.File;
import java.util.List;

public final class SourceSets {

    public static SourceSetContainer container(Project project) {
        return project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
    }

    public static NamedDomainObjectProvider<SourceSet> of(Project project, String name) {
        return container(project).register(name);
    }

    public static void linkSource(NamedDomainObjectProvider<SourceSet> $, Object sourceDir) {
        $.configure(sourceSet -> sourceSet.getJava().setSrcDirs(List.of(sourceDir)));
    }

    public static Provider<File> source(NamedDomainObjectProvider<SourceSet> sourceSet) {
        return sourceSet.map(SourceSet::getAllJava)
                .map(SourceDirectorySet::getSourceDirectories)
                .map(FileCollection::getSingleFile);
    }

    public static Provider<File> compiledClasses(NamedDomainObjectProvider<SourceSet> sourceSet) {
        return sourceSet.map(SourceSet::getOutput)
                .map(SourceSetOutput::getClassesDirs)
                .map(FileCollection::getSingleFile);
    }

    private SourceSets() { }

}
