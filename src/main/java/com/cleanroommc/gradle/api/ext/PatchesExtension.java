package com.cleanroommc.gradle.api.ext;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.provider.Property;

/**
 * Source-patch development environments, used by the loader workspace and available in other modes.
 */
public abstract class PatchesExtension {

    /**
     * When true, an {@code initial} patch-dev environment is registered against decompiled SRG sources.
     * Only meaningful in {@link ProjectMode#LOADER}.
     */
    public abstract Property<Boolean> getDevelopInitial();

    public abstract NamedDomainObjectContainer<PatchDevEnvironment> getPatchDev();

    public void patchDev(Action<? super NamedDomainObjectContainer<PatchDevEnvironment>> action) {
        action.execute(getPatchDev());
    }

}
