package com.cleanroommc.gradle.api.task;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.util.Objects;

public abstract class MavenJarExec extends LazilyConstructedJavaExec {

    public MavenJarExec(String configurationName, String artifactNotation) {
        var configuration = Objects.config(this.getProject(), configurationName);
        Objects.dependency(this.getProject(), configuration, artifactNotation);
        this.classpath(configuration);
        this.setWorkingDir(CleanroomExtension.get(this.getProject()).getLocalCacheDirectory().dir(this.getName()));
    }

}
