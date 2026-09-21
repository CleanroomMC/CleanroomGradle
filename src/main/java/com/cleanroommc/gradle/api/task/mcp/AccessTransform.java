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
import com.cleanroommc.gradle.api.util.IO;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;

import java.io.File;

@CacheableTask
public abstract class AccessTransform extends MavenJarExec {

    @Optional
    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getAccessTransformers();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputJar();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    public AccessTransform() {
        this.defaultLogFile("accesstransform.log");
        this.getMainClass().convention("net.minecraftforge.accesstransformer.TransformerProcessor");
    }

    @Override
    protected void beforeExec() {
        if (this.getUseDefaultToolArguments().get()) {
            this.args(
                    "--inJar",
                    this.getInputJar(),
                    "--outJar",
                    this.getOutputJar(),
                    "--logFile",
                    this.getLogFile().map(RegularFile::getAsFile).map(File::getName)
            );
            for (var accessTransformer : this.getAccessTransformers()) {
                this.args("--atFile", accessTransformer.getAbsolutePath());
            }
        }
        super.beforeExec();
    }

    @Override
    protected void afterExec() {
        IO.normalizeZip(this.getOutputJar().get().getAsFile().toPath());
        super.afterExec();
    }

}
