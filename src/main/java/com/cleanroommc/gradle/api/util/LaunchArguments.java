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

import com.cleanroommc.launcherutils.rule.RuleContext;
import com.cleanroommc.launcherutils.version.Substitutions;
import com.cleanroommc.launcherutils.version.VersionJson;
import com.cleanroommc.platformutils.Platform;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Renders the Minecraft launch arguments for a {@link VersionJson} independent of Gradle.
 *
 * <p>JVM arguments skip the classpath pair ({@code -cp}/{@code ${classpath}}, supplied by JavaExec) and any
 * {@code -Djava.library.path=} entry, which the run task sets itself. An unknown placeholder becomes an empty string
 * and warns once.</p>
 */
public final class LaunchArguments {

    private final VersionJson meta;
    private final RuleContext context;
    private final Substitutions substitutions;

    public LaunchArguments(
        VersionJson meta, Map<String, String> substitutions, Platform platform, Consumer<String> warn) {
        this.meta = meta;
        this.context = RuleContext.of(platform);
        var warned = new HashSet<String>();
        this.substitutions = Substitutions.create().putAll(substitutions).onMissing(name -> {
            if (warned.add(name)) {
                warn.accept("No substitution for launch argument placeholder ${" + name + "}; using an empty string.");
            }
            return "";
        });
    }

    // Broad, but meh
    private static boolean shouldSkipJvmArgument(String raw) {
        return raw.equals("-cp") ||
            raw.equals("-classpath") ||
            raw.equals("--class-path") ||
            raw.contains("${classpath}") ||
            raw.startsWith("-Djava.library.path=");
    }

    public boolean hasGameArguments() {
        return this.meta.hasGameArguments();
    }

    public List<String> gameArguments() {
        return this.meta.gameArguments(this.context, this.substitutions);
    }

    public List<String> jvmArguments() {
        var result = new ArrayList<String>();
        for (var argument : this.meta.jvmArgumentList()) {
            if (!argument.allowed(this.context)) {
                continue;
            }
            for (var raw : argument.values()) {
                if (!shouldSkipJvmArgument(raw)) {
                    result.add(this.substitutions.apply(raw));
                }
            }
        }
        return result;
    }

}
