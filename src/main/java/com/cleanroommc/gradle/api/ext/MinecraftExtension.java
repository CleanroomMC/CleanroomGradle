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
