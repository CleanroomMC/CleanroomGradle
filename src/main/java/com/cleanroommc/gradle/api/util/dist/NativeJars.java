/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util.dist;

import com.cleanroommc.launcherutils.Coordinate;
import com.cleanroommc.launcherutils.LauncherNames;

import java.util.Map;

/**
 * What {@code cleanroom.natives} declares about each per-platform jar, keyed by {@code group:module:classifier}.
 * Family jars go on the classpath, where Cleanroom loads their natives itself.
 * Jars no family declares fall back to their classifier, where {@code natives-*} means extracted.
 *
 * @param platforms the platform id of each family jar
 */
public record NativeJars(Map<String, String> platforms) {

    public static String key(String group, String module, String classifier) {
        return group + ":" + module + ":" + classifier;
    }

    private static String key(Coordinate coordinate) {
        return key(coordinate.group(), coordinate.artifact(), coordinate.classifier());
    }

    /**
     * @return the platform id a family maps this jar to, or null when no family declares it
     */
    public String platform(Coordinate coordinate) {
        return coordinate.classifier() == null ? null : this.platforms.get(key(coordinate));
    }

    public boolean isExtracted(Coordinate coordinate) {
        if (coordinate.classifier() == null) {
            return false;
        }
        return !this.platforms.containsKey(key(coordinate)) &&
            coordinate.classifier().startsWith(LauncherNames.NATIVES_PREFIX);
    }

}
