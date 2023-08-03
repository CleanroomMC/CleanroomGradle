package com.cleanroommc.gradle.dependency;

public enum Mapping {
    MCP("mcp");

    public static Mapping parse(String mapping) {
        try {
            return Mapping.valueOf(mapping.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException(String.format("%s mapping provider not supported!", mapping));
        }
    }

    private final String value;

    Mapping(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
