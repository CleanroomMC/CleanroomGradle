/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.task.mcp;

import com.cleanroommc.gradle.api.task.MavenJarExec;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

@CacheableTask
public abstract class MergeJars extends MavenJarExec {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getClientJar();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getServerJar();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract Property<Boolean> getInjectAnnotationMarkers();

    @OutputFile
    public abstract RegularFileProperty getMergedJar();

    public MergeJars() {
        this.getMainClass().convention("net.minecraftforge.mergetool.ConsoleMerger");
        this.getInjectAnnotationMarkers().convention(false);
    }

    @Override
    protected void beforeExec() {
        if (!this.getUseDefaultToolArguments().get()) {
            return;
        }
        this.args(
                "--client",
                this.getClientJar(),
                "--server",
                this.getServerJar(),
                "--output",
                this.getMergedJar(),
                "-ann",
                this.getMinecraftVersion(),
                "--inject",
                this.getInjectAnnotationMarkers()
        );
    }

}
