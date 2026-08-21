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
