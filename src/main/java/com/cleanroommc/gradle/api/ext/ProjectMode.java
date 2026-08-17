package com.cleanroommc.gradle.api.ext;

/** Selects which CleanroomGradle task pipeline a project exposes. */
public enum ProjectMode {

    /** Register only the shared vanilla and MCP facilities. */
    VANILLA,
    /** Register the Cleanroom loader-development and distribution pipelines. */
    LOADER,
    /** Register the mod-development pipeline backed by a Cleanroom userdev artifact. */
    USERDEV

}
