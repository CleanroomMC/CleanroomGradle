package com.cleanroommc.gradle.api.task;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Executes a Maven-provided tool in an external JVM")
public abstract class MavenJarExec extends LazilyConstructedJavaExec {

    @Classpath
    public abstract ConfigurableFileCollection getToolClasspath();

    /**
     * Whether the task should construct the command line expected by its default tool. Disable this and use
     * {@link #setArgs(Iterable)} when replacing the tool with one that has a different CLI.
     */
    @Input
    public abstract Property<Boolean> getUseDefaultToolArguments();

    public MavenJarExec() {
        this.getUseDefaultToolArguments().convention(true);
        this.classpath(this.getToolClasspath());
    }

}
