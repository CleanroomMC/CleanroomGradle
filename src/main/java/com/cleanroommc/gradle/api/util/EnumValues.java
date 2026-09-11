/*
 * Copyright (c) 2022-2026 CleanroomMC contributors
 *
 * This file is licensed under the CleanroomMC License Version 1.0.
 * See the applicable LICENSE file in this directory or a parent directory
 * for the full licence terms.
 *
 * This is visible-source software and is not open-source software.
 */

package com.cleanroommc.gradle.api.util;

import org.gradle.api.InvalidUserDataException;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class EnumValues {

    public static <T extends Enum<T>> T parse(Class<T> type, String value) {
        var constants = type.getEnumConstants();
        if (value == null || value.isBlank()) {
            throw new InvalidUserDataException("Missing " + type.getSimpleName() + ". Valid values: " + values(constants));
        }
        var normalized = value.trim().replace('-', '_');
        for (var constant : constants) {
            if (constant.name().equalsIgnoreCase(normalized)) {
                return constant;
            }
        }
        throw new InvalidUserDataException("Unknown " + type.getSimpleName() + " '" + value + "'. Valid values: " + values(constants));
    }

    private static <T extends Enum<T>> String values(T[] constants) {
        return Arrays.stream(constants).map(Enum::name).collect(Collectors.joining(", "));
    }

    private EnumValues() {}

}
