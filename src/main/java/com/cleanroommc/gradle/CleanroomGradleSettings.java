/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle;

import com.cleanroommc.gradle.api.util.dist.Repository;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;

/**
 * To inject the dependency repositories a Cleanroom workspace needs, for the consuming
 * builds do not have to declare them by hand.
 *
 * <p>Applied in {@code settings.gradle(.kts)} via {@code id("com.cleanroommc.cleanroomgradle.settings")},
 * after the plugin itself has been resolved through the consumer's own
 * {@code pluginManagement.repositories} (that bootstrap step cannot be short-circuited by this
 * plugin, since it has to run first to make this class available at all).
 *
 * <p>The repositories are registered on each project before its buildscript is evaluated.
 * When a consumer declares project repositories, it appends and extends, rather than overriding our repo set.
 */
public class CleanroomGradleSettings implements Plugin<Settings> {

    @Override
    public void apply(Settings settings) {
        settings.getGradle().getLifecycle().beforeProject(Repository::addTo);
    }

}
