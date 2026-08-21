package com.cleanroommc.gradle.api.ext;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;

/**
 * Mod-workspace settings. Used when {@link ProjectMode#USERDEV}.
 */
public abstract class UserdevExtension {

    /**
     * Cleanroom version whose {@code userdev} artifact this workspace is built from.
     */
    public abstract Property<String> getVersion();

    /**
     * Extra access transformers applied to Minecraft, in SRG member names.
     * Combined with the transformers shipped inside the userdev artifact.
     */
    public abstract ConfigurableFileCollection getAccessTransformers();

}
