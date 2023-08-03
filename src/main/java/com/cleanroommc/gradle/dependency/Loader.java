package com.cleanroommc.gradle.dependency;

public enum Loader {
    VANILLA("vanilla"),
    FORGE("forge"),
    CLEANROOM("cleanroom");

    public static Loader parse(String loader) {
        try {
            return Loader.valueOf(loader.toUpperCase());
        } catch (IllegalArgumentException e) {
           throw new UnsupportedOperationException(String.format("%s loader not supported!", loader));
        }
    }

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
