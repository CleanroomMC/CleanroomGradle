package com.cleanroommc.gradle.dependency;

public enum Loader {
    VANILLA("vanilla"),
    FORGE("forge"),
    CLEANROOM("cleanroom");

    private final String value;

    Loader(String value) {
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
