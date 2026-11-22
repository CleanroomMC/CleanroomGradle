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

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/**
 * Shared and project-local directories the toolchain writes into.
 */
public abstract class CachesExtension {

    public abstract DirectoryProperty getDirectory();

    public abstract DirectoryProperty getVersionDirectory();

    public abstract DirectoryProperty getLocalDirectory();

    public abstract Property<Boolean> getDiscardIntermediates();

}
