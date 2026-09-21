/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.task.sas;

import com.cleanroommc.gradle.api.task.MavenJarExec;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

/**
 * Runs installertools' {@code extract_inheritance} task against the injected srg-named jar to produce a
 * class/method inheritance map, consumed by {@link CheckSAS}.
 */
@CacheableTask
public abstract class ExtractInheritance extends MavenJarExec {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputJar();

    @Classpath
    public abstract ConfigurableFileCollection getLibraries();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    public ExtractInheritance() {
        this.defaultLogFile("extractInheritance.log");
        this.getMainClass().set("net.minecraftforge.installertools.ConsoleTool");
        this.args("--task", "extract_inheritance", "--input", this.getInputJar(), "--output", this.getOutput(), "--annotations");
    }

    @Override
    protected void beforeExec() {
        for (var library : this.getLibraries()) {
            this.args("--lib", library.getAbsolutePath());
        }
        super.beforeExec();
    }

}
