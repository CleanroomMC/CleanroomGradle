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

    private EnumValues() { }

}
