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

/**
 * Selects which CleanroomGradle task pipeline a project exposes.
 */
public enum ProjectMode {

    /**
     * Vanilla download, run, and decompile tasks only.
     */
    VANILLA,
    /**
     * Cleanroom loader sources, SAS/AT processing, run tasks, and distribution artifacts.
     */
    LOADER,
    /**
     * Mod workspace backed by a Cleanroom userdev artifact.
     */
    USERDEV

}
