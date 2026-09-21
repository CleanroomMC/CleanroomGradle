/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.task;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import javax.inject.Inject;

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

    @Inject
    public abstract ProviderFactory getProviders();

    public MavenJarExec() {
        this.getUseDefaultToolArguments().convention(true);
        this.classpath(this.getToolClasspath());
    }

    protected void defaultLogFile(String fileName) {
        this.getLogFile().fileProvider(this.getProviders().provider(() -> new File(this.getWorkingDir(), fileName)));
    }

}
