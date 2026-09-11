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

import com.cleanroommc.gradle.api.schema.VersionMeta;
import org.gradle.api.provider.Property;

/**
 * Version metadata for the primary Minecraft toolchain (loader/userdev, and the unsuffixed vanilla tasks).
 */
public abstract class MinecraftExtension {

    public abstract Property<String> getVersionMetaUrl();

    public abstract Property<VersionMeta> getVersionMeta();

}
