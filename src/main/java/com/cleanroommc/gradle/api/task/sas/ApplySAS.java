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

import com.cleanroommc.gradle.api.util.sas.SideOnlyHandler;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

/**
 * Removes configured legacy {@code @SideOnly} annotations without removing their bytecode.
 */
@CacheableTask
public abstract class ApplySAS extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputJar();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getSideAnnotationStrippers();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    public void apply() throws IOException {
        var result = SideOnlyHandler.applySas(
                this.getInputJar().get().getAsFile().toPath(),
                this.getOutputJar().get().getAsFile().toPath(),
                this.getSideAnnotationStrippers().getFiles().stream().map(File::toPath).toList()
        );
        this.getLogger().lifecycle("Removed {} legacy @SideOnly annotations", result.annotationsRemoved());
    }

}
