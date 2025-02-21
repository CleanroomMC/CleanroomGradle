package com.cleanroommc.gradle.api.util.lazy;

import com.cleanroommc.gradle.api.task.Tasks;
import com.cleanroommc.gradle.api.util.Objects;
import org.gradle.api.Action;
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
import org.gradle.api.tasks.compile.JavaCompile;

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

    public static void extendFromConfiguration(Project project, NamedDomainObjectProvider<SourceSet> $, NamedDomainObjectProvider<Configuration> configuration) {
        $.configure(sourceSet -> {
            var config = Objects.resolvedConfig(project, sourceSet.getImplementationConfigurationName());
            config.extendsFrom(configuration.get());
        });
    }

    public static Provider<String> compile(NamedDomainObjectProvider<SourceSet> sourceSet) {
        return sourceSet.map(SourceSet::getCompileJavaTaskName);
    }

    public static void configureCompile(Project $1, NamedDomainObjectProvider<SourceSet> $2, Action<JavaCompile> action) {
        $1.afterEvaluate(project -> $2.configure(sourceSet -> Tasks.<JavaCompile>named(project, sourceSet.getCompileJavaTaskName()).configure(action)));
    }

    public static Provider<File> source(NamedDomainObjectProvider<SourceSet> sourceSet) {
        return sourceSet.map(SourceSet::getAllJava)
                .map(SourceDirectorySet::getSourceDirectories)
                .map(FileCollection::getSingleFile);
    }

    public static Provider<File> classes(NamedDomainObjectProvider<SourceSet> sourceSet) {
        return sourceSet.map(SourceSet::getOutput)
                .map(SourceSetOutput::getClassesDirs)
                .map(FileCollection::getSingleFile);
    }

    private SourceSets() { }

}
