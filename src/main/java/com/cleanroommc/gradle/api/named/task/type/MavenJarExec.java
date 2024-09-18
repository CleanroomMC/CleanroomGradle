package com.cleanroommc.gradle.api.named.task.type;

import com.cleanroommc.gradle.api.named.Configurations;
import com.cleanroommc.gradle.api.structure.Locations;
import org.gradle.api.artifacts.Configuration;

public abstract class MavenJarExec extends LazilyConstructedJavaExec {
    
    public MavenJarExec(String name, String artifactNotation) {
        var project = getProject();
        var configuration = Configurations.of(project, name, true);
        provideMavenArtifacts(artifactNotation, configuration);
        setWorkingDir(Locations.temp(project, getName()));
    }
    
    private void provideMavenArtifacts(String artifactNotation, Configuration configuration) {
        getProject().getDependencies().add(configuration.getName(), artifactNotation);
        classpath(configuration);
    }

}
