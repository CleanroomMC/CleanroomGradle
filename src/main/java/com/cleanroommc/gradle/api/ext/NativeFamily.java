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

import org.gradle.api.Named;
import org.gradle.api.provider.MapProperty;

import javax.inject.Inject;

/**
 * Libraries that publish one jar per platform under a classifier naming scheme of their own.
 * Its name is the configuration such libraries are declared in, without classifiers. Usage:
 *
 * <pre>{@code
 * cleanroom.natives {
 *     lwjglNative {
 *         classifiers = ['linux-x64': 'natives-linux', 'linux-arm64': 'natives-linux-arm64', 'windows-x64': 'natives-windows']
 *     }
 * }
 * dependencies {
 *     lwjglNative 'org.lwjgl:lwjgl:3.4.3'
 * }
 * }</pre>
 */
public abstract class NativeFamily implements Named {

    private final String name;

    @Inject
    public NativeFamily(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    public abstract MapProperty<String, String> getClassifiers();

}
