/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.process.ExecOperations;
import org.gradle.process.JavaExecSpec;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class Execs {

    /**
     * Runs a tool with its console output buffered and replayed only when it fails.
     * The merge, access transformer and renamer tools each print every entry they process,
     * which buries a fresh workspace's first build under thousands of lines.
     */
    public static void quietJavaExec(ExecOperations operations, Action<? super JavaExecSpec> action) {
        var output = new ByteArrayOutputStream();
        var result = operations.javaexec(spec -> {
            spec.setStandardOutput(output);
            spec.setErrorOutput(output);
            spec.setIgnoreExitValue(true);
            action.execute(spec);
        });
        if (result.getExitValue() != 0) {
            throw new GradleException(
                    "Tool exited with code " + result.getExitValue() + ':' + System.lineSeparator() + output.toString(StandardCharsets.UTF_8)
            );
        }
    }

    private Execs() { }

}
