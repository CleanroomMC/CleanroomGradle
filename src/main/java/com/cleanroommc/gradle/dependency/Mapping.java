package com.cleanroommc.gradle.dependency;

public enum Mapping {
    MCP("mcp");

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
