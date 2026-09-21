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

import com.cleanroommc.gradle.api.schema.UserdevConfig;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

/**
 * Reads {@link UserdevConfig} out of a userdev artifact.
 */
public abstract class UserdevConfigValueSource implements ValueSource<UserdevConfig, UserdevConfigValueSource.Parameters> {

    public interface Parameters extends ValueSourceParameters {

        RegularFileProperty getUserdevJar();

    }

    @Override
    public UserdevConfig obtain() {
        return UserdevConfig.readFromJar(getParameters().getUserdevJar().getAsFile().get());
    }

}
