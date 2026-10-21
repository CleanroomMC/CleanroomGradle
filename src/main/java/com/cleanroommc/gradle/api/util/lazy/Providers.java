/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util.lazy;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

public final class Providers {

    private Providers() { }

    public static Provider<JavaLanguageVersion> targetJavaVersion(Project project) {
        return project.getExtensions().getByType(JavaPluginExtension.class).getToolchain().getLanguageVersion();
    }

    public static Provider<Integer> targetJavaMajor(Project project) {
        return targetJavaVersion(project).map(JavaLanguageVersion::asInt);
    }

    public static Provider<String> targetJavaName(Project project) {
        return targetJavaMajor(project).map(Providers::javaRuntimeName);
    }

    // mojang jre names
    public static String javaRuntimeName(int major) {
        if (major >= 29) {
            return "";
        }
        if (major >= 25) {
            return "java-runtime-epsilon";
        }
        if (major >= 21) {
            return "java-runtime-delta";
        }
        if (major >= 17) {
            return "java-runtime-beta";
        }
        if (major >= 16) {
            return "java-runtime-alpha";
        }
        return "jre-legacy";
    }

    public static Provider<JavaLauncher> javaLauncher(Project project) {
        return javaLauncher(project.getExtensions().getByType(JavaToolchainService.class), targetJavaVersion(project));
    }

    public static Provider<JavaLauncher> javaLauncher(JavaToolchainService toolchains, int api) {
        return toolchains.launcherFor(spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(api)));
    }

    public static Provider<JavaLauncher> javaLauncher(
        JavaToolchainService toolchains, Provider<JavaLanguageVersion> api) {
        return toolchains.launcherFor(spec -> spec.getLanguageVersion().set(api));
    }

}
