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

import com.cleanroommc.gradle.api.util.inject.MetadataInjector;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

/**
 * Adds the metadata a decompiler needs to the SRG named jar.
 * <ul>
 *     <li>Visibilities from {@code access.txt}</li>
 *     <li>{@code throws} clauses from {@code exceptions.txt}</li>
 *     <li>Parameter names keyed off {@code constructors.txt}</li>
 * </ul>
 */
@CacheableTask
public abstract class InjectMetadata extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSrgJar();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getAccessFile();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getConstructorsFile();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getExceptionsFile();

    @OutputFile
    public abstract RegularFileProperty getInjectedJar();

    @TaskAction
    public void inject() throws IOException {
        var result = MetadataInjector.inject(
                this.getSrgJar().get().getAsFile().toPath(),
                this.getInjectedJar().get().getAsFile().toPath(),
                this.getAccessFile().get().getAsFile().toPath(),
                this.getConstructorsFile().get().getAsFile().toPath(),
                this.getExceptionsFile().get().getAsFile().toPath()
        );
        this.getLogger()
                .lifecycle(
                        "Injected metadata into {} classes, copied {} entries, recorded {} abstract methods",
                        result.classesProcessed(),
                        result.entriesCopied(),
                        result.abstractMethodsRecorded()
                );
    }

}
