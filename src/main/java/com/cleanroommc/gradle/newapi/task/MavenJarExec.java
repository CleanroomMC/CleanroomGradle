package com.cleanroommc.gradle.newapi.task;

import com.cleanroommc.gradle.newapi.ext.CleanroomExtension;
import com.cleanroommc.gradle.newapi.util.Objects;

public abstract class MavenJarExec extends LazilyConstructedJavaExec {

    public MavenJarExec(String configurationName, String artifactNotation) {
        var configuration = Objects.config(this.getProject(), configurationName);
        Objects.dependency(this.getProject(), configuration, artifactNotation);
        this.classpath(configuration);
        this.setWorkingDir(Objects.extension(this.getProject(), CleanroomExtension.class).getLocalCacheDirectory().dir(this.getName()));
    }

}
