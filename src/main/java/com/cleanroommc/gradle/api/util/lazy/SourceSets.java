package com.cleanroommc.gradle.api.util.lazy;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
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

    public static NamedDomainObjectProvider<SourceSet> internal(Project project, String name) {
        var sourceSet = container(project).register(name);
        sourceSet.configure(internalSourceSet -> project.getTasks().named(internalSourceSet.getClassesTaskName())
                .configure(task -> task.setGroup(null)));
        return sourceSet;
    }

    public static void linkSource(NamedDomainObjectProvider<SourceSet> sourceSet, Object sourceDir) {
        sourceSet.configure(value -> value.getJava().setSrcDirs(List.of(sourceDir)));
    }

    public static void extendFromConfiguration(Project project, NamedDomainObjectProvider<SourceSet> sourceSet, NamedDomainObjectProvider<Configuration> configuration) {
        sourceSet.configure(value -> project.getConfigurations().named(value.getImplementationConfigurationName())
                .configure(config -> config.extendsFrom(configuration.get()))
        );
    }

    public static Provider<String> compile(NamedDomainObjectProvider<SourceSet> sourceSet) {
        return sourceSet.map(SourceSet::getCompileJavaTaskName);
    }

    public static Provider<File> source(NamedDomainObjectProvider<SourceSet> sourceSet) {
        return sourceSet.map(SourceSet::getAllJava).map(SourceDirectorySet::getSourceDirectories).map(FileCollection::getSingleFile);
    }

    public static Provider<File> classes(NamedDomainObjectProvider<SourceSet> sourceSet) {
        return sourceSet.map(SourceSet::getOutput).map(SourceSetOutput::getClassesDirs).map(FileCollection::getSingleFile);
    }

    private SourceSets() { }

}
