package com.cleanroommc.gradle.api.task;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.util.Objects;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

public abstract class MavenJarExec extends LazilyConstructedJavaExec {

    @Input
    public abstract Property<String> getExecJar();

    public MavenJarExec(String configurationName, String artifactNotation) {
        this.getExecJar().convention(artifactNotation);
        this.classpath(this.getExecJar().map(notation -> Objects.detachedConfig(this.getProject(), Objects.dependency(this.getProject(), notation))));
        this.setWorkingDir(CleanroomExtension.get(this.getProject()).getLocalCacheDirectory().dir(this.getName()));
    }

}
