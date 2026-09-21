/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.source;

import com.cleanroommc.gradle.api.Meta;
import com.cleanroommc.gradle.api.schema.VersionMeta;
import com.cleanroommc.gradle.api.util.IO;

import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

public abstract class BundledVersionMetaValueSource implements ValueSource<VersionMeta, ValueSourceParameters.None> {

    @Override
    public VersionMeta obtain() {
        var version = Meta.ONE_TRUE_MINECRAFT_VERSION;
        var stream = BundledVersionMetaValueSource.class.getResourceAsStream("/meta/" + version + ".json");
        if (stream == null) {
            throw new RuntimeException("Bundled " + version + " version meta not found in plugin resources");
        }
        return IO.readJson(stream, VersionMeta.class);
    }

}
