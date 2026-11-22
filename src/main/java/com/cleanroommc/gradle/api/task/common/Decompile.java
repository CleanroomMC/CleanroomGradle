/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.task.common;

import com.cleanroommc.gradle.api.task.MavenJarExec;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the Cleanflower decompiler. Decompiler options are passed as free-form key/value pairs through
 * {@link #getOptions()} rather than dedicated properties, since the tool can be swapped out
 * and each tool version (may) define its own option set.
 *
 * <p>The default tool is {@code com.cleanroommc:cleanflower:1.0.0}, resolved through the {@code decompiler}
 * configuration. Adding a dependency to that configuration replaces the default. The option names documented
 * on {@link #getOptions()} is the cleanflower v1.0.0 option set.</p>
 */
@CacheableTask
public abstract class Decompile extends MavenJarExec {

    public Decompile() {
        this.getMainClass().convention("org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler");
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("new-line-separator", true);
        defaults.put("ascii-strings", true);
        defaults.put("include-classpath", true);
        defaults.put("jad-style-variable-naming", true);
        defaults.put("thread-count", -1);
        defaults.put("indent-string", "    ");
        this.getOptions().convention(defaults);
        this.getOnlyClasses().convention(List.of());
        this.getSilent().convention(false);
        this.getExtraArgs().convention(List.of());
    }

    private static String argValue(Object value) {
        return value instanceof Boolean bool ? (bool ? "1" : "0") : value.toString();
    }

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getCompiledJar();

    @Optional
    @Classpath
    @InputFiles
    public abstract ConfigurableFileCollection getLibraries();

    @Input
    public abstract MapProperty<String, Object> getOptions();

    @Input
    @Optional
    public abstract ListProperty<String> getOnlyClasses();

    @Input
    @Optional
    public abstract Property<Boolean> getSilent();

    @Input
    public abstract ListProperty<String> getExtraArgs();

    @OutputFile
    public abstract RegularFileProperty getDecompiledJar();

    @Override
    protected void beforeExec() {
        this.getLogger()
            .lifecycle("Using Java {} to decompile", this.getJavaLauncher().get().getMetadata().getLanguageVersion());
        if (!this.getUseDefaultToolArguments().get()) {
            return;
        }
        for (var entry : this.getOptions().get().entrySet()) {
            this.args("--" + entry.getKey() + "=" + argValue(entry.getValue()));
        }
        // Extra args must precede --silent/-only/-e as the CLI stops parsing --option=value arguments after
        this.args(this.getExtraArgs().get());
        if (this.getSilent().getOrElse(false)) {
            this.args("--silent");
        }
        for (var prefix : this.getOnlyClasses().get()) {
            this.args("-only=" + prefix);
        }
        for (var file : this.getLibraries().getFiles()) {
            this.args("-e=" + file.getAbsolutePath());
        }
        this.args(this.getCompiledJar(), this.getDecompiledJar());
    }

}
