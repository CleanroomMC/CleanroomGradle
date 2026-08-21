package com.cleanroommc.gradle.api.ext;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/**
 * Shared and project-local directories the toolchain writes into.
 */
public abstract class CachesExtension {

    /**
     * Gradle-user-home (or overridden) cache for Minecraft downloads and versioned artifacts.
     */
    public abstract DirectoryProperty getDirectory();

    /**
     * Per-Minecraft-version slice of {@link #getDirectory()}, used by the 1.12.2 toolchain.
     */
    public abstract DirectoryProperty getVersionDirectory();

    /**
     * Project-local generated data, typically under {@code build/}.
     */
    public abstract DirectoryProperty getLocalDirectory();

    /**
     * When true, intermediate pipeline files are deleted after their last consumer runs.
     * Defaults true except in {@link ProjectMode#LOADER}.
     */
    public abstract Property<Boolean> getDiscardIntermediates();

}
