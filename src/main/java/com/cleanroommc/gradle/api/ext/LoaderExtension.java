/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.ext;

import com.cleanroommc.gradle.api.util.LwjglNatives;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import java.util.List;

/**
 * Loader-workspace and distribution settings. Used when {@link ProjectMode#LOADER}.
 */
public abstract class LoaderExtension {

    public LoaderExtension() {
        getForgeVersion().convention("14.23.5.2864");
        getInstallerVersion().convention("0.2.1");
        getLaunchClass().convention("top.outlands.foundation.boot.Foundation");
        getClientLaunchClass().convention(getLaunchClass());
        getServerLaunchClass().convention(getLaunchClass());
        getClientTweakClass().convention("net.minecraftforge.fml.common.launcher.FMLTweaker");
        getServerTweakClass().convention("net.minecraftforge.fml.common.launcher.FMLServerTweaker");
        getInstallerJvmArgs().convention(List.of());
        getLwjglNativesClassifiers().convention(LwjglNatives.CLASSIFIERS);
        getIntermediateRuns().convention(false);
    }

    public abstract Property<String> getForgeVersion();

    public abstract Property<String> getInstallerVersion();

    public abstract Property<String> getLaunchClass();

    public abstract Property<String> getClientLaunchClass();

    public abstract Property<String> getServerLaunchClass();

    public abstract Property<String> getClientTweakClass();

    public abstract Property<String> getServerTweakClass();

    public abstract ListProperty<String> getInstallerJvmArgs();

    public abstract ConfigurableFileCollection getAccessTransformers();

    public abstract ConfigurableFileCollection getSideAnnotationStrippers();

    public abstract ListProperty<String> getLwjglNativesClassifiers();

    public abstract Property<Boolean> getIntermediateRuns();

}
